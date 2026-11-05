package org.libaray_manager_system.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.LoginMapper;
import org.libaray_manager_system.mapper.UserMapper;
import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserRegisterDto;
import org.libaray_manager_system.pojo.dto.UserSelectDto;
import org.libaray_manager_system.pojo.entity.sysUser;
import org.libaray_manager_system.pojo.vo.LoginVo;
import org.libaray_manager_system.pojo.vo.UserVo;
import org.libaray_manager_system.service.LoginService;
import org.libaray_manager_system.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private LoginMapper loginmapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 登录
     * 整个流程就 5 步，按顺序做：
     *   1. 校验参数不为空
     *   2. 按用户名把人查出来
     *   3. 校验密码（BCrypt）
     *   4. 校验账号有没有被禁用
     *   5. 签发 token，组装返回
     *
     * ⚠️ Mapper 只负责"查数据库"，它给不了你 token ——
     *    token 必须在这里用 JwtUtil 现签。
     */
    @Override
    public LoginVo Login(LoginDto loginDto) {

        // ---------- 1. 参数基本校验 ----------
        if (loginDto.getUsername() == null || loginDto.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (loginDto.getPassword() == null || loginDto.getPassword().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        // ---------- 2. 按用户名查人 ----------
        sysUser user = loginmapper.GetByUsername(loginDto.getUsername().trim());

        // ---------- 3. 校验密码 ----------
        // 注意这里把两个分支合并成了一句：
        //   用户不存在(user == null) 和 密码错，返回【完全相同】的提示语。
        //   如果分开提示（"用户不存在"/"密码错误"），攻击者就能拿一堆用户名去试，
        //   靠报错文案判断哪些用户名真实存在（这叫用户名枚举）。
        //   顺带：user == null 时短路了，不会执行 BCrypt.checkpw，也不会有空指针。
        if (user == null || !BCrypt.checkpw(loginDto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // ---------- 4. 账号是否被禁用 ----------
        // 放在密码校验之后：密码都不对的人，没必要告诉他"你的号被禁了"
        // ⚠️ 两个 null 判断都要：status 是 Integer，直接和 0 比较会拆箱 NPE
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException("该账号已被禁用，请联系管理员");
        }

        // ---------- 5. 签发 token ----------
        String token = JwtUtil.createToken(
                user.getId().longValue(),   // 实体的 id 是 Integer，工具类要 Long
                user.getUsername(),
                user.getRole());
        // ---------- 6. 组装返回 ----------
        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        loginVo.setUser(toVo(user));
        return loginVo;
    }

    /**
     * 获取当前登录用户的完整信息（接口 2：GET /auth/me）
     *
     * <p>和登录返回的 {@code user} 段【结构完全一致】，前端拿到可以直接覆盖本地缓存。
     *
     * @param id 由拦截器从 token 解析出来，不是前端传的
     */
    @Override
    public UserVo GetVOById(Integer id) {
        if (id == null) {
            throw new BusinessException("未登录或登录已过期");
        }

        sysUser user = loginmapper.GetById(id);
        if (user == null) {
            throw new BusinessException("账号不存在或已被删除");
        }
        return toVo(user);
    }
    private UserVo toVo(sysUser user) {
        UserVo vo = new UserVo();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setAvatar(user.getAvatar());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    @Override
    public void Register(UserRegisterDto userRegisterDto) {
        if(userRegisterDto.getUsername() == null || userRegisterDto.getPassword() == null){
            throw new BusinessException("用户名或密码不能为空");
        }
        if(userRegisterDto.getUsername().length() < 3||userRegisterDto.getUsername().length()>50){
            throw new BusinessException("用户名长度必须在3-50之间");
        }
        sysUser olduser = loginmapper.GetByUsername(userRegisterDto.getUsername());
        if(olduser != null){
            throw new BusinessException("用户名已存在");
        }
//        判断手机号格式
        if(userRegisterDto.getPhone() != null && !userRegisterDto.getPhone().matches("^1[3456789]\\d{9}$")){
            throw new BusinessException("手机号格式错误");
        }
        if(userRegisterDto.getEmail() != null && !userRegisterDto.getEmail().matches("^\\w+@[\\w]+\\.\\w+$")){
            throw new BusinessException("邮箱格式错误");
        }
        //        注册
        String password = userRegisterDto.getPassword();
        password = BCrypt.hashpw(password,BCrypt.gensalt());
        UserSelectDto user = new UserSelectDto();
        user.setUsername(userRegisterDto.getUsername());
        user.setPassword(password);
        user.setRealName(userRegisterDto.getRealName());
        user.setPhone(userRegisterDto.getPhone());
        user.setEmail(userRegisterDto.getEmail());
        user.setStatus(1);
        user.setRole("USER");
        userMapper.Insert(user);
    }

}
