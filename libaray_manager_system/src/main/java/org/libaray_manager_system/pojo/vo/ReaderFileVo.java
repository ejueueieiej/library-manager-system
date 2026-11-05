// ========== ⑧ pojo/vo/ReaderFileVo.java ==========
package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderFileVo {
    private Long id;
    private String title;
    private Integer wordCount;
    private Integer chapterCount;
    private LocalDateTime createTime;
}