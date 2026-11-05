// ========== ⑥ pojo/vo/ChapterItemVo.java ==========
package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChapterItemVo {
    private Integer index;
    private String title;
    private Integer wordCount;
}