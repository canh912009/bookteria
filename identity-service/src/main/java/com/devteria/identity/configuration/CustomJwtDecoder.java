package com.devteria.identity.configuration;

import java.text.ParseException;
import java.util.Date;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.devteria.identity.repository.InvalidatedTokenRepository;
import com.devteria.identity.repository.UserSessionRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomJwtDecoder implements JwtDecoder {
    InvalidatedTokenRepository invalidatedTokenRepository;
    UserSessionRepository userSessionRepository;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            var now = new Date();

            if (!signedJWT.verify(verifier)
                    || signedJWT.getJWTClaimsSet().getExpirationTime().before(now)
                    || invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID())) {
                throw new JwtException("Invalid token");
            }

            var sessionId = signedJWT.getJWTClaimsSet().getStringClaim("sid");
            if (Objects.isNull(sessionId)) throw new JwtException("Invalid token");

            var session = userSessionRepository.findById(sessionId).orElseThrow(() -> new JwtException("Invalid token"));
            if (session.isRevoked() || session.getExpiresAt().before(now)) throw new JwtException("Invalid token");

            session.setLastSeenAt(now);
            userSessionRepository.save(session);

            return new Jwt(token,
                    signedJWT.getJWTClaimsSet().getIssueTime().toInstant(),
                    signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(),
                    signedJWT.getHeader().toJSONObject(),
                    signedJWT.getJWTClaimsSet().getClaims()
            );

        } catch (ParseException | JOSEException e) {
            throw new JwtException("Invalid token");
        }
    }
}
