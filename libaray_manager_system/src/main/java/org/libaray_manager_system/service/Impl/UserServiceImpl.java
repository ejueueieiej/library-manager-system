package org.libaray_manager_system.service.Impl;

import cn.hutool.crypto.digest.BCrypt;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.BorrowMapper;
import org.libaray_manager_system.mapper.LoginMapper;
import org.libaray_manager_system.mapper.UserMapper;
import org.libaray_manager_system.pojo.dto.LoginDto;
import org.libaray_manager_system.pojo.dto.UserDeleteDto;
import org.libaray_manager_system.pojo.dto.UserSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.entity.sysUser;
import org.libaray_manager_system.pojo.vo.UserVo;
import org.libaray_manager_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private LoginMapper loginMapper;
    @Autowired
    private BorrowMapper borrowMapper;
    @Override
    public void UpdatePassword(LoginUser loginUser,LoginDto loginDto) {
        if(loginDto.getNewPassword()==null||loginDto.getOldPassword()==null
                ||loginDto.getNewPassword().isEmpty()||loginDto.getOldPassword().isEmpty()){
            throw new BusinessException("新密码和旧密码不能为空");
        }
        if(loginDto.getOldPassword().equals(loginDto.getNewPassword())){
            throw new BusinessException("新密码不能与旧密码相同");
        }
        if(!BCrypt.checkpw(loginDto.getOldPassword(),loginMapper.GetByUsername(loginUser.getUsername()).getPassword())){
            throw new BusinessException("旧密码错误");
        }
//        更新密码
        userMapper.UpdatePassword(loginUser.getUsername(),BCrypt.hashpw(loginDto.getNewPassword(),BCrypt.gensalt()));

    }

    @Override
    public List<UserVo> GetUsers(UserSelectDto userSelectDto) {
        Integer startIndex = (userSelectDto.getPageNum()-1)*userSelectDto.getPageSize();
        return userMapper.GetUsers(userSelectDto,startIndex);
    }


    @Override
    public void UpdateUser(Integer id, UserVo userVo,LoginUser loginUser) {
        UserVo olduser = userMapper.GetUserById(id);
        if(olduser==null){
            throw new BusinessException("用户不存在");
        }

        if(userVo.getUsername()!=null&&!olduser.getUsername().equals(userVo.getUsername())){
            throw new BusinessException("用户名不能更新");
        }

        if(userVo.getRealName()==null){
            throw new BusinessException("真实姓名不能为空");
        }

        if(olduser.getRole().equals("ADMIN")&&!olduser.getRole().equals(userVo.getRole())){
            throw new BusinessException("管理员角色的身份权限不能更新");
        }

        if((olduser.getRole().equals("ADMIN")&&loginUser.getUsername().equals(userVo.getUsername()))&&(!olduser.getStatus().equals(userVo.getStatus())))
        {
            throw new BusinessException("管理员用户不能更新自己的状态");
        }
        userMapper.UpdateById(userVo,id);
    }
//    新增用户
    @Override
    public void AddUser(UserSelectDto userSelectDto) {
        if(userMapper.GetUserByUsername(userSelectDto.getUsername())!=null){
            throw new BusinessException("用户名已存在");
        }
        if(userMapper.GetUserByRealName(userSelectDto.getRealName())!=null){
            throw new BusinessException("真实姓名已存在");
        }
        if(userSelectDto.getUsername()==null||userSelectDto.getPassword()==null){
            throw new BusinessException("用户名或密码不能为空");
        }
        if(userSelectDto.getPassword().length()<6||userSelectDto.getPassword().length()>20){
            throw new BusinessException("密码长度必须在6到20之间");
        }
//        存储密码要转哈希！
        userSelectDto.setPassword(BCrypt.hashpw(userSelectDto.getPassword(),BCrypt.gensalt()));
        userMapper.Insert(userSelectDto);
    }

//    管理员重置密码
    @Override
    public void ResetPassword(Integer id, String newPassword) {
        if(newPassword==null){
            throw new BusinessException("新密码不能为空");
        }
        if(newPassword.length()<6||newPassword.length()>20){
            throw new BusinessException("密码长度必须在6到20之间");
        }
        if(userMapper.GetUserById(id)==null){
            throw new BusinessException("用户不存在");
        }
        userMapper.UpdatePassword(userMapper.GetUserById(id).getUsername(),BCrypt.hashpw(newPassword,BCrypt.gensalt()));
    }

    //用户状态更改按钮
    @Override
    public Integer UpdateStatus(Integer id, Integer status,LoginUser loginUser) {
        if(status==null){
            throw new BusinessException("状态不能为空");
        }
        if(userMapper.GetUserById(id)==null){
            throw new BusinessException("用户不存在");
        }
        if(userMapper.GetUserById(id).getUsername().equals(loginUser.getUsername())){
            throw new BusinessException("不能更改自己的状态");
        }
        return userMapper.UpdateStatus(id,status);
    }

    //删除指定用户
    @Override
    public void DeleteUser(Integer id, LoginUser loginUser) {
        if(userMapper.GetUserById(id)==null){
            throw new BusinessException("用户不存在");
        }
        if(userMapper.GetUserById(id).getRole().equals("ADMIN")){
            throw new BusinessException("管理员用户不能删除，如有必要请联系yz删库");
        }
        UserVo olduser = userMapper.GetUserById(id);
        if(olduser.getUsername().equals(loginUser.getUsername())){
            throw new BusinessException("不能删除自己的账号");
        }
        // 有未归还的书就不能删：user_id 会指向不存在的人，库存永远少一本，书也永远删不掉
        if(borrowMapper.CountBorrowingByUser(id)>0){
            throw new BusinessException("该用户还有未归还的图书，不能删除");
        }
        //删除用户
        userMapper.DeleteUser(id);

    }
//    用户注销
    @Override
    public void LogoutUser(UserDeleteDto userDeleteDto, LoginUser loginUser) {
        if(userDeleteDto.getPassword()==null){
            throw new BusinessException("请输入密码以确认注销");
        }
        String oldpassword = userMapper.GetUserByUsername(loginUser.getUsername()).getPassword();
        if(!BCrypt.checkpw(userDeleteDto.getPassword(),oldpassword)){
            throw new BusinessException("密码错误");
        }
        int admincount=userMapper.GetAdminUserCount();
        if(loginUser.getRole().equals("ADMIN")&&admincount==1){
            throw new BusinessException("最后一个管理员用户不能注销");
        }
        // 顺序放在验密码之后：不让没输对密码的人探测出这个账号有没有欠书
        if(borrowMapper.CountBorrowingByUser(loginUser.getId())>0){
            throw new BusinessException("你还有未归还的图书，请先归还后再注销账号");
        }
        userMapper.DeleteByUsername(loginUser.getUsername());
    }
    @Override
    public Integer GetUserCount() {
        return userMapper.GetUserCount();
    }
}
