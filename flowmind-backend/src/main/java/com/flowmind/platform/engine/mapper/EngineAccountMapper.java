package com.flowmind.platform.engine.mapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface EngineAccountMapper {
    @Select("SELECT token_quota FROM sys_user WHERE id = #{id} AND status = 1")
    Integer quota(Long id);
    @Select("SELECT token_quota FROM sys_user WHERE id = #{id} AND status = 1 FOR UPDATE")
    Integer lockQuota(Long id);
    @Update("UPDATE sys_user SET token_quota = token_quota - #{tokens}, token_used = token_used + #{tokens} WHERE id = #{id} AND status = 1 AND token_quota >= #{tokens}")
    int debit(@Param("id") Long id, @Param("tokens") int tokens);
    @Insert("INSERT INTO sys_model_audit_log(user_id, username, module_name, action_type, model_provider, cost_tokens, duration_ms, detail_json, http_status) VALUES(#{id}, #{username}, 'ENGINE', 'EXEC_AI', #{provider}, #{tokens}, #{duration}, #{detail}, 200)")
    void audit(@Param("id") Long id, @Param("username") String username, @Param("provider") String provider,
               @Param("tokens") int tokens, @Param("duration") int duration, @Param("detail") String detail);
}
