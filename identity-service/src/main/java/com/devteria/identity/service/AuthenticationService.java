package com.devteria.identity.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
    private static final int MAX_ACTIVE_SESSIONS = 3;

    UserRepository userRepository;
    InvalidatedTokenRepository invalidatedTokenRepository;
    UserSessionRepository userSessionRepository;

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
                .userId(
                        Objects.nonNull(jwt)
                                ? jwt.getJWTClaimsSet().getSubject()
                        : null)
                .valid(isValid).build();
    }

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        var user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());

        if (!authenticated) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var sessionId = UUID.randomUUID().toString();
        var tokenInfo = generateToken(user, sessionId);
        var now = new Date();

        userSessionRepository.save(UserSession.builder()
                .id(sessionId)
                .userId(user.getId())
                .tokenId(tokenInfo.tokenId())
                .deviceId(request.getDeviceId())
                .deviceName(request.getDeviceName())
                .createdAt(now)
                .lastSeenAt(now)
                .expiresAt(tokenInfo.refreshableExpiryDate())
                .revoked(false)
                .build());

        enforceSessionLimit(user.getId(), now);

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
        } catch (AppException exception){
            log.info("Token already expired");
        }
    }

    @Transactional
    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);

        invalidateToken(signedJWT);

        var username = signedJWT.getJWTClaimsSet().getSubject();

        var user =
                userRepository.findByUsername(username).orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        var tokenInfo = generateToken(user, sessionId);
        var session = userSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        session.setTokenId(tokenInfo.tokenId());
        session.setLastSeenAt(new Date());
        session.setExpiresAt(tokenInfo.refreshableExpiryDate());
        userSessionRepository.save(session);

        return AuthenticationResponse.builder()
                .token(tokenInfo.token())
                .expiryTime(tokenInfo.expiryDate())
                .sessionId(sessionId)
                .build();
    }

    private TokenInfo generateToken(User user, String sessionId) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);
        var issueTime = new Date();
        var expiryTime = new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli());
        var refreshableExpiryTime = new Date(Instant.now().plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS).toEpochMilli());
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

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = (isRefresh)
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime()
                .toInstant().plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS).toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        if (Objects.isNull(sessionId)) throw new AppException(ErrorCode.UNAUTHENTICATED);

        var now = new Date();
        var session = userSessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (session.isRevoked() || session.getExpiresAt().before(now)) throw new AppException(ErrorCode.UNAUTHENTICATED);

        session.setLastSeenAt(now);
        userSessionRepository.save(session);

        return signedJWT;
    }

    private void enforceSessionLimit(String userId, Date now) {
        var activeSessions = userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                userId, now);

        while (activeSessions.size() > MAX_ACTIVE_SESSIONS) {
            var sessionToRevoke = activeSessions.remove(0);
            sessionToRevoke.setRevoked(true);
            sessionToRevoke.setRevokedReason("KICKED_BY_NEW_LOGIN");
            userSessionRepository.save(sessionToRevoke);
            invalidatedTokenRepository.save(InvalidatedToken.builder()
                    .id(sessionToRevoke.getTokenId())
                    .expiryTime(sessionToRevoke.getExpiresAt())
                    .build());
        }
    }

    private void revokeSession(SignedJWT signedJWT, String reason) throws ParseException {
        var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
        if (Objects.isNull(sessionId)) return;

        userSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setRevoked(true);
            session.setRevokedReason(reason);
            userSessionRepository.save(session);
        });
    }

    private void invalidateToken(SignedJWT signedJWT) throws ParseException {
        var invalidatedToken = InvalidatedToken.builder()
                .id(signedJWT.getJWTClaimsSet().getJWTID())
                .expiryTime(signedJWT.getJWTClaimsSet().getExpirationTime())
                .build();

        invalidatedTokenRepository.save(invalidatedToken);
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
