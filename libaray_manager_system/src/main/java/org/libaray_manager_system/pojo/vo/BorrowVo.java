package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowVo {
    private Long id;
    private Integer userId;
    private String username;
    private String userName;
    private Integer bookId;
    private String bookName;
    private String bookIsbn;
    private String bookCoverUrl;
    private LocalDateTime borrowTime;
    private LocalDateTime dueTime;
    private LocalDateTime returnTime;
    private Integer status;// 1借出中 2已归还 3已取消
    private Integer overdueDays;// 不是数据库列，Service 里算好塞进来
    private Integer renewCount;
    private Integer canRenew;// 不是数据库列，同上
}
