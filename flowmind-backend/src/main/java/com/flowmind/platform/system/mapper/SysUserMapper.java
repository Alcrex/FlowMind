package com.flowmind.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmind.platform.system.entity.SysUser;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    SysUser lockById(Long id);

    @Update("UPDATE sys_user SET token_quota = token_quota - #{cost}, token_used = token_used + #{cost}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 1 AND token_quota >= #{cost} AND token_used <= 2147483647 - #{cost}")
    int consumeTokens(@Param("id") Long id, @Param("cost") int cost);
}
