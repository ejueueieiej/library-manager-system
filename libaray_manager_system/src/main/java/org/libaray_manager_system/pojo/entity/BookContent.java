// ========== ① pojo/entity/BookContent.java ==========
package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookContent {
    private Integer bookId;
    private String content;
    private Integer wordCount;
    private String encoding;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}