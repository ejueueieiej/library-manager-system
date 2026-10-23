package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowSelectDto {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String username = "";
    private String bookName = "";
    private Integer status;// 1/2/3，空=全部
    private Integer overdue;// 1=只看逾期
}
