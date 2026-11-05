package org.libaray_manager_system.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

public class ChapterSpiltTest {
    @Test
    public void chapterSpiltTest() {
        String text = "第一章 潜入\n他推开门。\n他翻到第三章的时候睡着了。\n第二章 回声\n水声传来。\n";
        List<ChapterSplitUtils.Chapter> list = ChapterSplitUtils.split(text);

        System.out.println("章节数：" + list.size());                 // 期望 2
        list.forEach(c -> System.out.println("  " + c.title()));     // 期望：第一章 潜入 / 第二章 回声
    }
}
