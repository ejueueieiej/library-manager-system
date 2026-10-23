package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.libaray_manager_system.pojo.dto.UserSelectDto;
import org.libaray_manager_system.pojo.vo.UserVo;
import org.libaray_manager_system.pojo.entity.sysUser;


import java.util.List;

@Mapper
public interface  UserMapper {

    void UpdatePassword(String username, String newPassword);

    List<UserVo> GetUsers(UserSelectDto userSelectDto,int startIndex);

    UserVo GetUserById(Integer id);

    void UpdateById(UserVo userVo,Integer id);

    sysUser GetUserByUsername(String username);

    sysUser GetUserByRealName(String realName);

    void Insert(UserSelectDto userSelectDto);

    Integer UpdateStatus(Integer id, Integer status);

    void DeleteUser(Integer id);

    int GetAdminUserCount();

    void DeleteByUsername(String username);

    Integer GetUserCount();
}
