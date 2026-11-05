// ========== ③ pojo/entity/ReaderProgress.java ==========
package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderProgress {
    private Long id;
    private Integer userId;
    private Integer targetType;
    private Long targetId;
    private Integer chapterIndex;
    private String chapterTitle;
    private BigDecimal percent;
    private LocalDateTime updateTime;
}