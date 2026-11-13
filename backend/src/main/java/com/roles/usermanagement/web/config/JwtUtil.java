package com.roles.usermanagement.web.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/** Signs and verifies tokens using security.jwt configuration. */
@Component
@EnableConfigurationProperties(JwtProperties.class)
public class JwtUtil {
    private final JwtProperties properties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtUtil(JwtProperties properties) {
        this.properties=properties;
        this.algorithm=Algorithm.HMAC256(properties.secret());
        this.verifier=JWT.require(algorithm).withIssuer(properties.issuer()).build();
    }

    /** Claim con la versión de credenciales de la cuenta al emitir el token (B04). */
    public static final String CREDENTIAL_VERSION_CLAIM = "cv";

    public String create(String username, int credentialVersion) {
        Instant now=Instant.now();
        return JWT.create().withSubject(username).withIssuer(properties.issuer())
                .withClaim(CREDENTIAL_VERSION_CLAIM, credentialVersion)
                .withIssuedAt(now).withExpiresAt(now.plus(properties.expiration())).sign(algorithm);
    }

    public boolean isValid(String token) {
        if(token==null || token.isBlank()) return false;
        try {
            verifier.verify(token);
            return true;
        } catch(JWTVerificationException exception) {
            return false;
        }
    }

    /** Versión de credenciales del token, o null si no la incluye (token anterior a B04: no aceptable). */
    public Integer getCredentialVersion(String token) {
        return verifier.verify(token).getClaim(CREDENTIAL_VERSION_CLAIM).asInt();
    }

    public String getUsername(String token) {
        return verifier.verify(token).getSubject();
    }
}
