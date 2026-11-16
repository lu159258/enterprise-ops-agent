package com.luzihao.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.api.request.LoginRequest;
import com.luzihao.core.auth.JwtService;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.SysRole;
import com.luzihao.infra.mapper.SysRoleMapper;
import com.luzihao.infra.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AppUserService appUserService;
    @Autowired
    private SysRoleMapper sysRoleMapper;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            authenticationManager.authenticate(           // 内部走 AppUserDetailsService 查库 + BCrypt 比对
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "用户名或密码错误"));
        }
        // 组装 UserInfo（RBAC：角色集合来自 user_role→sys_role）
        AppUser u = appUserService.getOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, req.username()));
        UserInfo info = new UserInfo();
        info.setUserId(u.getId());
        info.setTenantId(u.getTenantId());
        List<SysRole> sysRoleList = sysRoleMapper.selectRoleCodesByUserId(u.getId());
        Set<String> roleCodeSet = new HashSet<>();
        List<String> allowedServices = new ArrayList<>();
        for (SysRole sysRole : sysRoleList) {
            roleCodeSet.add(sysRole.getCode());
            if (StringUtils.hasText(sysRole.getAllowedServices())) {
                String[] split = sysRole.getAllowedServices().split(",");
                allowedServices.addAll(List.of(split));
            }

        }
        info.setRoles(roleCodeSet);
        info.setAllowedServices(allowedServices);
        return ResponseEntity.ok(Map.of(
                "accessToken", jwtService.issueToken(info),
                "tokenType", "Bearer",
                "expiresIn", 7200));
    }
}
