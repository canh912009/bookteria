package com.devteria.identity.service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.devteria.identity.dto.request.AuthenticationRequest;
import com.devteria.identity.dto.request.IntrospectRequest;
import com.devteria.identity.dto.request.LogoutRequest;
import com.devteria.identity.dto.request.RefreshRequest;
import com.devteria.identity.dto.response.AuthenticationResponse;
import com.devteria.identity.dto.response.IntrospectResponse;
import com.devteria.identity.entity.InvalidatedToken;
import com.devteria.identity.entity.User;
import com.devteria.identity.entity.UserSession;
import com.devteria.identity.exception.AppException;
import com.devteria.identity.exception.ErrorCode;
import com.devteria.identity.repository.InvalidatedTokenRepository;
import com.devteria.identity.repository.UserRepository;
import com.devteria.identity.repository.UserSessionRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    private static final int MAX_ACTIVE_SESSIONS = 2;
    private static final long LAST_SEEN_SYNC_INTERVAL_MS = 5 * 60 * 1000L;
    private static final String BLACKLIST_TOKEN_PREFIX = "auth:blacklist:jti:";
    private static final String SESSION_CACHE_PREFIX = "auth:session:";
    private static final String REVOKED_MARKER = "REVOKED";
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(10);

    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    UserSessionRepository userSessionRepository;
    StringRedisTemplate stringRedisTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESHABLE_DURATION;

    public IntrospectResponse introspect(IntrospectRequest request) throws ParseException {
        var token = request.getToken();
        boolean isValid = true;
        SignedJWT jwt = null;

        try {
            jwt = verifyToken(token, false);
        } catch (AppException | JOSEException | ParseException e) {
            isValid = false;
        }

        return IntrospectResponse.builder()
                .userId(Objects.nonNull(jwt) ? jwt.getJWTClaimsSet().getSubject() : null)
                .valid(isValid)
                .build();
    }

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = PASSWORD_ENCODER.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var sessionId = UUID.randomUUID().toString();
        var tokenInfo = generateToken(user, sessionId);
        var now = new Date();

        var newSession = UserSession.builder()
                .id(sessionId)
                .userId(user.getId())
                .tokenId(tokenInfo.tokenId())
                .deviceId(request.getDeviceId())
                .deviceName(request.getDeviceName())
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(tokenInfo.refreshableExpiryDate())
                .revoked(false)
                .build();

        userSessionRepository.save(newSession);
        cacheActiveSession(newSession);

        enforceSessionLimit(user.getId(), sessionId, request.getDeviceId(), now);

        return AuthenticationResponse.builder()
                .token(tokenInfo.token())
                .expiryTime(tokenInfo.expiryDate())
                .sessionId(sessionId)
                .build();
    }

    @Transactional
    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            var signToken = verifyToken(request.getToken(), true);

            invalidateToken(signToken);
            revokeSession(signToken, "LOGOUT");
        } catch (AppException exception) {
            log.info("Token already expired");
        }
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);

        invalidateToken(signedJWT);

        var subject = signedJWT.getJWTClaimsSet().getSubject();

        var user = userRepository
                .findById(subject)
                .or(() -> userRepository.findByUsername(subject))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        var tokenInfo = generateToken(user, sessionId);
        var session = userSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        session.setTokenId(tokenInfo.tokenId());
        session.setLastSeenAt(new Date());
        session.setExpiresAt(tokenInfo.refreshableExpiryDate());
        userSessionRepository.save(session);
        cacheActiveSession(session);

        return AuthenticationResponse.builder()
                .token(tokenInfo.token())
                .expiryTime(tokenInfo.expiryDate())
                .sessionId(sessionId)
                .build();
    }

    public SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = (isRefresh)
                ? new Date(signedJWT
                        .getJWTClaimsSet()
                        .getIssueTime()
                        .toInstant()
                        .plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        var now = new Date();
        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(now))) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var jti = signedJWT.getJWTClaimsSet().getJWTID();
        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        if (Objects.isNull(sessionId)) throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (isTokenBlacklistedInRedis(jti)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (verifyAndTouchSessionInRedis(sessionId, now)) {
            return signedJWT;
        }

        if (invalidatedTokenRepository.existsById(jti)) {
            blacklistTokenInRedis(jti, expiryTime);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var session = userSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (session.isRevoked() || session.getExpiresAt().before(now)) {
            markSessionRevokedInRedis(sessionId, session.getExpiresAt());
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        if (shouldSyncLastSeen(session.getLastSeenAt(), now)) {
            session.setLastSeenAt(now);
            userSessionRepository.save(session);
        }
        cacheActiveSession(session.getId(), session.getExpiresAt(), now);

        return signedJWT;
    }

    private TokenInfo generateToken(User user, String sessionId) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        var issueTime = new Date();
        var expiryTime =
                new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli());
        var refreshableExpiryTime = new Date(
                Instant.now().plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS).toEpochMilli());
        var tokenId = UUID.randomUUID().toString();

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId())
                .issuer("devteria.com")
                .issueTime(issueTime)
                .expirationTime(expiryTime)
                .jwtID(tokenId)
                .claim("sid", sessionId)
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return new TokenInfo(jwsObject.serialize(), expiryTime, refreshableExpiryTime, tokenId);
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    private void enforceSessionLimit(String userId, String currentSessionId, String deviceId, Date now) {
        var activeSessions =
                userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(userId, now);

        if (StringUtils.hasText(deviceId)) {
            var iterator = activeSessions.iterator();
            while (iterator.hasNext()) {
                var existing = iterator.next();
                if (!existing.getId().equals(currentSessionId) && deviceId.equals(existing.getDeviceId())) {
                    revokeAndInvalidateSession(existing, "RELOGIN_SAME_DEVICE");
                    iterator.remove();
                }
            }
        }

        while (activeSessions.size() > MAX_ACTIVE_SESSIONS) {
            var sessionToRevoke = activeSessions.remove(0);
            if (sessionToRevoke.getId().equals(currentSessionId) && activeSessions.size() > 0) {
                sessionToRevoke = activeSessions.remove(0);
            }
            revokeAndInvalidateSession(sessionToRevoke, "KICKED_BY_NEW_LOGIN");
        }
    }

    private void revokeAndInvalidateSession(UserSession session, String reason) {
        session.setRevoked(true);
        session.setRevokedReason(reason);
        userSessionRepository.save(session);
        invalidatedTokenRepository.save(InvalidatedToken.builder()
                .id(session.getTokenId())
                .expiryTime(session.getExpiresAt())
                .build());
        markSessionRevokedInRedis(session.getId(), session.getExpiresAt());
        blacklistTokenInRedis(session.getTokenId(), session.getExpiresAt());
    }

    private void revokeSession(SignedJWT signedJWT, String reason) throws ParseException {
        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        if (Objects.isNull(sessionId)) return;

        userSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            session.setRevokedReason(reason);
            userSessionRepository.save(session);
            markSessionRevokedInRedis(sessionId, session.getExpiresAt());
        });
    }

    private void invalidateToken(SignedJWT signedJWT) throws ParseException {
        var jti = signedJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();
        var invalidatedToken =
                InvalidatedToken.builder().id(jti).expiryTime(expiryTime).build();

        invalidatedTokenRepository.save(invalidatedToken);
        blacklistTokenInRedis(jti, expiryTime);
    }

    private boolean shouldSyncLastSeen(Date lastSeenAt, Date now) {
        return Objects.isNull(lastSeenAt) || (now.getTime() - lastSeenAt.getTime() >= LAST_SEEN_SYNC_INTERVAL_MS);
    }

    private boolean isTokenBlacklistedInRedis(String jti) {
        if (Objects.isNull(jti)) return false;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_TOKEN_PREFIX + jti));
        } catch (Exception e) {
            log.debug("Redis unavailable when checking token blacklist, falling back to DB", e);
            return false;
        }
    }

    private void blacklistTokenInRedis(String jti, Date expiryTime) {
        if (Objects.isNull(jti) || Objects.isNull(expiryTime)) return;
        long ttlMillis = expiryTime.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) return;
        try {
            stringRedisTemplate.opsForValue().set(BLACKLIST_TOKEN_PREFIX + jti, "1", Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.debug("Redis unavailable when blacklisting token", e);
        }
    }

    private void cacheActiveSession(UserSession session) {
        cacheActiveSession(session.getId(), session.getExpiresAt(), session.getLastSeenAt());
    }

    private void cacheActiveSession(String sessionId, Date expiresAt, Date lastSeenAt) {
        if (Objects.isNull(sessionId) || Objects.isNull(expiresAt)) return;
        long ttlMillis = expiresAt.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) return;
        long lastSeenMillis = Objects.nonNull(lastSeenAt) ? lastSeenAt.getTime() : System.currentTimeMillis();
        String value = expiresAt.getTime() + ":" + lastSeenMillis;
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(SESSION_CACHE_PREFIX + sessionId, value, Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.debug("Redis unavailable when caching session", e);
        }
    }

    private void markSessionRevokedInRedis(String sessionId, Date expiresAt) {
        if (Objects.isNull(sessionId)) return;
        long ttlMillis =
                Objects.nonNull(expiresAt) ? expiresAt.getTime() - System.currentTimeMillis() : VALID_DURATION * 1000L;
        if (ttlMillis <= 0) ttlMillis = VALID_DURATION * 1000L;
        try {
            stringRedisTemplate
                    .opsForValue()
                    .set(SESSION_CACHE_PREFIX + sessionId, REVOKED_MARKER, Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.debug("Redis unavailable when marking session revoked", e);
        }
    }

    private boolean verifyAndTouchSessionInRedis(String sessionId, Date now) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(SESSION_CACHE_PREFIX + sessionId);
            if (Objects.isNull(cached)) {
                return false;
            }
            if (REVOKED_MARKER.equals(cached)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            String[] parts = cached.split(":");
            if (parts.length != 2) {
                return false;
            }
            long expiresAtMillis = Long.parseLong(parts[0]);
            long lastSyncedMillis = Long.parseLong(parts[1]);
            if (expiresAtMillis <= now.getTime()) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            if (now.getTime() - lastSyncedMillis >= LAST_SEEN_SYNC_INTERVAL_MS) {
                userSessionRepository.findById(sessionId).ifPresent(session -> {
                    session.setLastSeenAt(now);
                    userSessionRepository.save(session);
                });
                cacheActiveSession(sessionId, new Date(expiresAtMillis), now);
            }
            return true;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Redis unavailable when verifying session, falling back to DB", e);
            return false;
        }
    }

    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (!CollectionUtils.isEmpty(user.getRoles()))
            user.getRoles().forEach(role -> {
                stringJoiner.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions()))
                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
            });

        return stringJoiner.toString();
    }

    private record TokenInfo(String token, Date expiryDate, Date refreshableExpiryDate, String tokenId) {}
}
