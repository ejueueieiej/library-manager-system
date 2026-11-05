// ========== ② pojo/entity/ReaderFile.java ==========
package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderFile {
    private Long id;
    private Integer userId;
    private String title;
    private String content;
    private Integer wordCount;
    private Integer chapterCount;
    private String encoding;
    private LocalDateTime createTime;
}