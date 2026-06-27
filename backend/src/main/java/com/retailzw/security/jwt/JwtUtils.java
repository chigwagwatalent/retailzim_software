package com.retailzw.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
@Slf4j
public class JwtUtils {

    private static final Pattern BASE64_SECRET = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isBlank()) {
            throw new IllegalStateException("JWT secret must not be blank.");
        }

        byte[] keyBytes = looksBase64(secret)
                ? Decoders.BASE64.decode(secret)
                : secret.getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(keyBytes.length >= 64 ? keyBytes : sha512(keyBytes));
    }

    private boolean looksBase64(String secret) {
        return secret.length() % 4 == 0 && BASE64_SECRET.matcher(secret).matches();
    }

    private byte[] sha512(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-512 is not available for JWT key derivation.", ex);
        }
    }

    public String generateAccessToken(UserDetails userDetails, Long tenantId, Long branchId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", tenantId);
        claims.put("branchId", branchId);
        claims.put("role", role);

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token is expired: {}", ex.getMessage());
            throw ex;
        } catch (JwtException ex) {
            log.error("JWT token is invalid: {}", ex.getMessage());
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractTenantId(String token) {
        Claims claims = extractAllClaims(token);
        Object tenantId = claims.get("tenantId");
        if (tenantId instanceof Integer) return ((Integer) tenantId).longValue();
        if (tenantId instanceof Long) return (Long) tenantId;
        return tenantId != null ? Long.parseLong(tenantId.toString()) : null;
    }

    public Long extractBranchId(String token) {
        Claims claims = extractAllClaims(token);
        Object branchId = claims.get("branchId");
        if (branchId == null) return null;
        if (branchId instanceof Integer) return ((Integer) branchId).longValue();
        if (branchId instanceof Long) return (Long) branchId;
        return Long.parseLong(branchId.toString());
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}

