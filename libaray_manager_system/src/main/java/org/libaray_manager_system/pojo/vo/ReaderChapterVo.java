// ========== ⑦ pojo/vo/ReaderChapterVo.java ==========
package org.libaray_manager_system.pojo.vo;

import lombok.Data;
//一章正文
@Data
public class ReaderChapterVo {
    private Integer index;
    private String title;
    private String content;
    private Integer prevIndex;
    private Integer nextIndex;
}