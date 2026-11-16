package com.luzihao.core.auth;

import com.luzihao.core.user.UserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

@Service
public class JwtService {

    @Value("${app.auth.jwt-secret}")
    private String secret;
    @Value("${app.auth.jwt-expire-minutes}")
    private long expireMinutes;


    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // HS256 需 ≥32 字节
    }


    /** 登录成功后签发（roles 集合序列化为逗号串） */
    public String issueToken(UserInfo user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId())
                .claim("tenantId", user.getTenantId())
                .claim("roles", String.join(",", user.getRoles()))
                .claim("allowedServices", String.join(",", user.getAllowedServices()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireMinutes, ChronoUnit.MINUTES)))
                .signWith(key())                       // jjwt 0.12 API：自动选 HS256
                .compact();
    }

    /** 校验并解析；任何异常（签名错/过期/格式错）统一抛 JwtException，由 Filter 转 401 */
    public UserInfo parseAndVerify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        UserInfo user = new UserInfo();
        user.setUserId(claims.getSubject());
        user.setTenantId(claims.get("tenantId", String.class));
        user.setRoles(new HashSet<>(Arrays.asList(
                claims.get("roles", String.class).split(","))));
        user.setAllowedServices(List.of(
                claims.get("allowedServices", String.class).split(",")));
        return user;
    }
}
