package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.entity.sysUser;

@Mapper
public interface LoginMapper {

    /**
     * 按登录名查用户（登录、拦截器鉴权都要用）
     *
     * ⚠️ 返回类型必须是【实体 sysUser】而不是 UserVo ——
     *    因为登录要用 BCrypt.checkpw(明文, 哈希) 校验密码，
     *    而 UserVo 里故意没有 password 字段，拿它根本没法校验。
     */
    sysUser GetByUsername(@Param("username") String username);

    /**
     * 按主键查用户（「获取当前登录用户」用）
     *
     * <p>为什么不复用 GetByUsername？
     * 拦截器往 request 里塞的 LoginUser 只有 id / username / realName / role 四个字段，
     * 而 /auth/me 要返回手机号、邮箱、头像等完整信息，得回库补一次。
     * 按 id 查比按 username 查更稳 —— username 理论上是可以被管理员改掉的。
     */
    sysUser GetById(@Param("id") Integer id);

}
