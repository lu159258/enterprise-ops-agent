package com.luzihao.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT * FROM sys_role r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<SysRole> selectRoleCodesByUserId(String userId);
}
