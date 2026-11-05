package org.libaray_manager_system.controller.impl;

import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserDeleteDto;
import org.libaray_manager_system.pojo.dto.UserSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.UserPageResult;
import org.libaray_manager_system.pojo.vo.UserVo;
import org.libaray_manager_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    //用户更改密码
    @PutMapping("/auth/password")
    public Result UpdatePassword(@RequestBody LoginDto loginDto,
                                 @RequestAttribute("loginUser") LoginUser loginUser) {
        userService.UpdatePassword(loginUser, loginDto);
        return new Result().success("密码更新成功");
    }

    //获取所有用户
    @RequireAdmin
    @GetMapping("/users")
    public Result GetUsers(UserSelectDto userSelectDto) {
        List<UserVo> userVos = userService.GetUsers(userSelectDto);
        Integer total = userService.GetUserCount();
        UserPageResult pageResult = new UserPageResult(total, userVos);
        return new Result().success(pageResult);
    }

    //更新用户
    @RequireAdmin
    @PutMapping("/users/{id}")
    public Result UpdateUser(@PathVariable Integer id,
                             @RequestBody UserVo userVo,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        userService.UpdateUser(id, userVo,loginUser);
        return new Result().success("更新成功", null);
    }

    //新增用户
    @RequireAdmin
    @PostMapping("/users")
    public Result AddUser(@RequestBody UserSelectDto userSelectDto) {
        userService.AddUser(userSelectDto);
        return new Result().success("新增成功", null);
    }

    //管理员重置密码
    @RequireAdmin
    @PutMapping("/users/{id}/password")
    public Result ResetPassword(@PathVariable Integer id,
                                 @RequestBody LoginDto loginDto) {
        userService.ResetPassword(id,loginDto.getNewPassword());
        return new Result().success("密码重置成功");
    }

    //用户状态更改按钮
    @RequireAdmin
    @PatchMapping("/users/{id}/status")
    public Result UpdateStatus(@PathVariable Integer id,
                                @RequestParam Integer status,
                                @RequestAttribute("loginUser") LoginUser loginUser) {
        Integer result = userService.UpdateStatus(id,status,loginUser);
        if(result==0){
            return new Result().error("已禁用");
        }
        return new Result().success("已启用");

    }
//    删除指定用户
    @RequireAdmin
    @DeleteMapping("/users/{id}")
    public Result DeleteUser(@PathVariable Integer id,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        userService.DeleteUser(id,loginUser);
        return new Result().success("删除成功", null);
    }

//    用户注销
    @DeleteMapping("/auth/account")
    public Result Logout(@RequestAttribute("loginUser") LoginUser loginUser,
                         @RequestBody UserDeleteDto  userDeleteDto) {
        userService.LogoutUser(userDeleteDto,loginUser);
        return new Result().success("注销成功", null);
    }
}
