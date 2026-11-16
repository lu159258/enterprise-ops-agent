package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
