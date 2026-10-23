package org.libaray_manager_system.pojo.vo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//登录VO,登录返回参
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {
    private String token;
    private UserVo user;
}
