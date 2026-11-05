// ========== ⑤ pojo/vo/ReaderMetaVo.java ==========
package org.libaray_manager_system.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
//阅读页打开时拿到的目录＋状态
@Data
public class ReaderMetaVo {
    private String title;
    private Boolean readable;
    private Boolean hasContent;
    private String denyReason;
    private Integer chapterCount;
    private Integer wordCount;
    private String encoding;
    private Integer lastChapter;
    // 上次在那一章里滚到百分之几（0~100）—— 用来精确恢复阅读位置
    private BigDecimal lastPercent;
    private List<ChapterItemVo> chapters;
}