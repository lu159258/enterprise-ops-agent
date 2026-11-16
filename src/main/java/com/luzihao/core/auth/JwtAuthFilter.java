package com.luzihao.core.auth;

import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {
    @Autowired
    private JwtService jwtService;
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws ServletException, IOException {


        String header = req.getHeader("Authorization");
        // 没带 token 或不是 Bearer 形态 → 交给 Spring Security 后续判断（登录接口是放行的）
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, resp);
            return;
        }
        try {
            UserInfo user = jwtService.parseAndVerify(header.substring(7));
            // 写入 Spring Security 上下文 + 项目自己的 UserContext（保持业务代码零改动）
            UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken
                    .authenticated(user.getUserId(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            UserContext.set(user);
            chain.doFilter(req, resp);
        } catch (JwtException | IllegalArgumentException e) {
            log.error("doFilterInternal失败{}",e);
            // 签名伪造/过期/格式错 → 统一 401（不带细节，防止探测）
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"message\":\"token 无效或已过期\"}");
        } finally {
            UserContext.clear();     // 线程池复用，必须清理（沿袭现有拦截器规范）
        }
    }
}
