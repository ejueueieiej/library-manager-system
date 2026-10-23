package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSelectDto {
    Integer pageNum=1;
    Integer pageSize=10;
    String username="";
    String realName="";
    String role="";
    String email="";
    String phone="";
    String password="";//密码(一般不存储)
    Integer status;
}
