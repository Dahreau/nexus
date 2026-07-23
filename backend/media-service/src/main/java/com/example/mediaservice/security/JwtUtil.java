package com.example.mediaservice.security;

import java.security.Key;
import java.util.Date;
import java.util.Map;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class JwtUtil {
    // Prefer environment-provided secret in containerized or production deployments.
    private static final String SECRET = (System.getenv("JWT_SECRET") != null && !System.getenv("JWT_SECRET").isBlank())
        ? System.getenv("JWT_SECRET")
        : "ReplaceThisWithASecureRandomSecretKeyOfSufficientLength123!";
    private static final long EXP_MS = 1000L * 60 * 60 * 24;

    private static Key getSigningKey() {
        byte[] keyBytes = SECRET.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public static String generateToken(String userId, String role) {
        return Jwts.builder()
                .setClaims(Map.of("sub", userId, "role", role))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXP_MS))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
