package com.example.identity.util;

import com.example.identity.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final String secret;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret:${jwt.jwt-secret}}") String secret,
            @Value("${jwt.expiration:${jwt.jwt-expiration:3600000}}") long expiration) {
        this.secret = secret;
        this.expiration = expiration;
    }

    /**
     * Khởi tạo Key ký (HMAC SHA-256) từ chuỗi bí mật secret 256-bit
     */
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Tạo Token định danh từ đối tượng User (Bài 1):
     * - Header: alg=HS256, typ=JWT (được JJWT tạo tự động)
     * - Payload:
     *     + Claim chuẩn "sub" (Subject): username của User
     *     + Custom claim: "role" của User
     *     + Claim thời gian: "iat" (Issued At), "exp" (Expiration: hiện tại + 3600000ms)
     * - Signature: Ký bằng HMAC-SHA256 với secret 256-bit
     *
     * @param user Thông tin user đã lưu trong database
     * @return Chuỗi JWT (3 phần phân cách bởi dấu chấm)
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Trích xuất username từ Token (từ claim "sub")
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Trích xuất role từ Token (từ custom claim "role")
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Trích xuất thời điểm hết hạn của Token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Trích xuất một Claim cụ thể bằng resolver
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Trích xuất toàn bộ Claims từ Token và xác thực Signature
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Kiểm tra Token đã hết hạn chưa
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Xác thực Token có hợp lệ với User hay không
     */
    public boolean validateToken(String token, User user) {
        final String username = extractUsername(token);
        return (username.equals(user.getUsername()) && !isTokenExpired(token));
    }

    public long getExpiration() {
        return expiration;
    }
}
