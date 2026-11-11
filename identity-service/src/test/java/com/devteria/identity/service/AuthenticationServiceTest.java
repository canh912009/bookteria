package com.devteria.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.devteria.identity.dto.request.AuthenticationRequest;
import com.devteria.identity.dto.request.IntrospectRequest;
import com.devteria.identity.dto.request.RefreshRequest;
import com.devteria.identity.entity.User;
import com.devteria.identity.entity.UserSession;
import com.devteria.identity.exception.AppException;
import com.devteria.identity.repository.InvalidatedTokenRepository;
import com.devteria.identity.repository.UserRepository;
import com.devteria.identity.repository.UserSessionRepository;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String SIGNER_KEY = "1TjXchw5FloESb63Kc+DFhTARvpWL4jUGCwfGWxuG5SIf/1y/LgJxHnMqaF6A/ij";

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthenticationService authenticationService;
    private User user;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository, invalidatedTokenRepository, userSessionRepository, stringRedisTemplate);
        ReflectionTestUtils.setField(authenticationService, "SIGNER_KEY", SIGNER_KEY);
        ReflectionTestUtils.setField(authenticationService, "VALID_DURATION", 3600L);
        ReflectionTestUtils.setField(authenticationService, "REFRESHABLE_DURATION", 36000L);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        user = User.builder()
                .id("user-123")
                .username("john")
                .password(new BCryptPasswordEncoder(10).encode("12345678"))
                .build();
    }

    @Test
    void authenticate_sameDeviceRelogin_revokesPreviousDeviceSession() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        var now = new Date();
        var oldSameDeviceSession = UserSession.builder()
                .id("old-session-1")
                .userId("user-123")
                .tokenId("old-token-1")
                .deviceId("device-A")
                .lastSeenAt(new Date(now.getTime() - 60_000))
                .expiresAt(new Date(now.getTime() + 3_600_000))
                .revoked(false)
                .build();

        List<UserSession> activeSessions = new ArrayList<>();
        activeSessions.add(oldSameDeviceSession);

        when(userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                        eq("user-123"), any(Date.class)))
                .thenReturn(activeSessions);

        var response = authenticationService.authenticate(AuthenticationRequest.builder()
                .username("john")
                .password("12345678")
                .deviceId("device-A")
                .deviceName("Chrome Windows")
                .build());

        assertThat(response.getToken()).isNotBlank();
        assertThat(oldSameDeviceSession.isRevoked()).isTrue();
        assertThat(oldSameDeviceSession.getRevokedReason()).isEqualTo("RELOGIN_SAME_DEVICE");
        verify(invalidatedTokenRepository).save(argThat(t -> "old-token-1".equals(t.getId())));
        verify(valueOperations).set(eq("auth:session:old-session-1"), eq("REVOKED"), any());
    }

    @Test
    void authenticate_fourthDistinctDevice_kicksOldestSession() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        var now = new Date();
        var s1 = UserSession.builder()
                .id("s1")
                .userId("user-123")
                .tokenId("t1")
                .deviceId("dev-1")
                .lastSeenAt(new Date(now.getTime() - 300_000))
                .expiresAt(new Date(now.getTime() + 3_600_000))
                .build();
        var s2 = UserSession.builder()
                .id("s2")
                .userId("user-123")
                .tokenId("t2")
                .deviceId("dev-2")
                .lastSeenAt(new Date(now.getTime() - 200_000))
                .expiresAt(new Date(now.getTime() + 3_600_000))
                .build();
        var s3 = UserSession.builder()
                .id("s3")
                .userId("user-123")
                .tokenId("t3")
                .deviceId("dev-3")
                .lastSeenAt(new Date(now.getTime() - 100_000))
                .expiresAt(new Date(now.getTime() + 3_600_000))
                .build();
        var s4 = UserSession.builder()
                .id("s4")
                .userId("user-123")
                .tokenId("t4")
                .deviceId("dev-4")
                .lastSeenAt(now)
                .expiresAt(new Date(now.getTime() + 3_600_000))
                .build();

        List<UserSession> activeSessions = new ArrayList<>(List.of(s1, s2, s3, s4));
        when(userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                        eq("user-123"), any(Date.class)))
                .thenReturn(activeSessions);

        authenticationService.authenticate(AuthenticationRequest.builder()
                .username("john")
                .password("12345678")
                .deviceId("dev-4")
                .build());

        assertThat(s1.isRevoked()).isTrue();
        assertThat(s1.getRevokedReason()).isEqualTo("KICKED_BY_NEW_LOGIN");
        assertThat(s2.isRevoked()).isTrue();
        assertThat(s2.getRevokedReason()).isEqualTo("KICKED_BY_NEW_LOGIN");
        assertThat(s3.isRevoked()).isFalse();
    }

    @Test
    void introspect_redisCacheHit_skipsDatabaseQueriesAndWrites() throws Exception {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                        eq("user-123"), any(Date.class)))
                .thenReturn(new ArrayList<>());

        var authResponse = authenticationService.authenticate(AuthenticationRequest.builder()
                .username("john")
                .password("12345678")
                .deviceId("dev-1")
                .build());

        clearInvocations(userSessionRepository, invalidatedTokenRepository);

        long nowMillis = System.currentTimeMillis();
        long expiresAtMillis = nowMillis + 3_600_000L;
        when(stringRedisTemplate.hasKey(startsWith("auth:blacklist:jti:"))).thenReturn(false);
        when(valueOperations.get("auth:session:" + authResponse.getSessionId()))
                .thenReturn(expiresAtMillis + ":" + nowMillis);

        var introspectResponse = authenticationService.introspect(
                IntrospectRequest.builder().token(authResponse.getToken()).build());

        assertThat(introspectResponse.isValid()).isTrue();
        assertThat(introspectResponse.getUserId()).isEqualTo("user-123");
        verifyNoInteractions(invalidatedTokenRepository);
        verifyNoInteractions(userSessionRepository);
    }

    @Test
    void refreshToken_looksUpUserBySubjectIdAndUpdatesSession() throws Exception {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                        eq("user-123"), any(Date.class)))
                .thenReturn(new ArrayList<>());

        var authResponse = authenticationService.authenticate(AuthenticationRequest.builder()
                .username("john")
                .password("12345678")
                .deviceId("dev-1")
                .build());

        long nowMillis = System.currentTimeMillis();
        when(stringRedisTemplate.hasKey(startsWith("auth:blacklist:jti:"))).thenReturn(false);
        when(valueOperations.get("auth:session:" + authResponse.getSessionId()))
                .thenReturn((nowMillis + 3_600_000L) + ":" + nowMillis);

        var session = UserSession.builder()
                .id(authResponse.getSessionId())
                .userId("user-123")
                .tokenId("old-token")
                .expiresAt(new Date(nowMillis + 3_600_000L))
                .revoked(false)
                .build();
        when(userSessionRepository.findById(authResponse.getSessionId())).thenReturn(Optional.of(session));
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        var refreshResponse = authenticationService.refreshToken(
                RefreshRequest.builder().token(authResponse.getToken()).build());

        assertThat(refreshResponse.getToken()).isNotBlank();
        assertThat(refreshResponse.getSessionId()).isEqualTo(authResponse.getSessionId());
        verify(userRepository).findById("user-123");
    }

    @Test
    void verifyToken_sessionMarkedRevokedInRedis_throwsUnauthenticatedWithoutDbQuery() throws Exception {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(userSessionRepository.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(
                        eq("user-123"), any(Date.class)))
                .thenReturn(new ArrayList<>());

        var authResponse = authenticationService.authenticate(AuthenticationRequest.builder()
                .username("john")
                .password("12345678")
                .build());

        clearInvocations(userSessionRepository, invalidatedTokenRepository);
        when(stringRedisTemplate.hasKey(startsWith("auth:blacklist:jti:"))).thenReturn(false);
        when(valueOperations.get("auth:session:" + authResponse.getSessionId())).thenReturn("REVOKED");

        assertThrows(AppException.class, () -> authenticationService.verifyToken(authResponse.getToken(), false));
        verifyNoInteractions(userSessionRepository);
    }
}
