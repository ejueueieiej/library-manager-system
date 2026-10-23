package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserRegisterDto;
import org.libaray_manager_system.pojo.vo.LoginVo;
import org.libaray_manager_system.pojo.vo.UserVo;

public interface LoginService {

    /**
     * 登录：校验账号密码，通过则签发 token
     */
    LoginVo Login(LoginDto loginDto);

    UserVo GetVOById(Integer id);

    void Register(UserRegisterDto userRegisterDto);
}
