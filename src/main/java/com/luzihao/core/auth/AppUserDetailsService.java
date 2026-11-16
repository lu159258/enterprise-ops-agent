package com.luzihao.core.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.SysRole;
import com.luzihao.infra.mapper.SysRoleMapper;
import com.luzihao.infra.service.AppUserService;
import com.luzihao.infra.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    @Autowired
    private AppUserService appUserService;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = appUserService.getOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username));
        if (u == null || !Boolean.TRUE.equals(u.getEnabled())) {
            throw new UsernameNotFoundException(username);   // 统一"用户名或密码错误"，不区分不存在/禁用
        }

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
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(roleCodeSet.toArray(new String[0]))       // Spring Security 的 ROLE_ 前缀机制
                .build();
    }
}
