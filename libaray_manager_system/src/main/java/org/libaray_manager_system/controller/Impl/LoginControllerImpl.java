package org.libaray_manager_system.controller.Impl;


import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserRegisterDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.vo.UserVo;
import org.libaray_manager_system.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginControllerImpl {

    @Autowired
    private LoginService loginService;
//    注册用户
    @PostMapping("/auth/register")
    public Result Register(@RequestBody UserRegisterDto userRegisterDto) {
        loginService.Register(userRegisterDto);
        Result result=new Result();
        return result.success("注册成功");
    }

    @PostMapping("/auth/login")
    public Result Login(@RequestBody LoginDto loginDto) {
        return new Result().success("登录成功", loginService.Login(loginDto));
    }

    @GetMapping("/auth/me")
    public Result GetMe(@RequestAttribute("loginUser") LoginUser loginUser) {
        UserVo vo = loginService.   GetVOById(loginUser.getId());
        return new Result().success(vo);
    }

    @PostMapping("/auth/logout")
    public Result Logout() {
        return new Result().success("已退出登录", null);
    }
}
