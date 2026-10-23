package org.libaray_manager_system.pojo.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


//登录DTO,登陆传参用
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginDto {
    private String username;
    private String password;
    private String oldPassword;
    private String newPassword;
}
