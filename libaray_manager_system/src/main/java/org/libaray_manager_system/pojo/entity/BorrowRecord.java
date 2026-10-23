package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowRecord {
    private Long id;
    private Integer userId;
    private Integer bookId;
    private LocalDateTime borrowTime;// 借出时间
    private LocalDateTime dueTime;// 到期时间
    private LocalDateTime returnTime;// 未归还 / 已取消时为 null
    private Integer status;// 1借出中 2已归还 3已取消
    private Integer renewCount;// 续借次数
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
