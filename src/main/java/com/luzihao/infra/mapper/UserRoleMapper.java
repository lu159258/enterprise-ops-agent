package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
