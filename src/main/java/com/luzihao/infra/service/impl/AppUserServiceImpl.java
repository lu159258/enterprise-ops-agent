package com.luzihao.infra.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.mapper.AppUserMapper;
import com.luzihao.infra.service.AppUserService;
import org.springframework.stereotype.Service;

/**
 * Agent 任务 Service 实现
 */
@Service
public class AppUserServiceImpl extends ServiceImpl<AppUserMapper, AppUser> implements AppUserService {
}
