package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {
    private String username;
    private String password;
    private String realName;
    private String role="USER";
    private String email;
    private String phone;
}
