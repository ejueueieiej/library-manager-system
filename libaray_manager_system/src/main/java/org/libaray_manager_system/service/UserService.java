package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserDeleteDto;
import org.libaray_manager_system.pojo.dto.UserSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.vo.UserVo;
import java.util.List;

public interface UserService {
    void UpdatePassword(LoginUser loginUser,LoginDto loginDto);

    List<UserVo> GetUsers(UserSelectDto userSelectDto);

    void UpdateUser(Integer id, UserVo userVo,LoginUser loginUser);

    void AddUser(UserSelectDto userSelectDto);

    void ResetPassword(Integer id, String newPassword);

    Integer UpdateStatus(Integer id, Integer status,LoginUser loginUser);

    void DeleteUser(Integer id, LoginUser loginUser);

    void LogoutUser(UserDeleteDto userDeleteDto, LoginUser loginUser);

    Integer GetUserCount();
}
