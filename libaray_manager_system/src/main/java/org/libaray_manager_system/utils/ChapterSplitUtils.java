package org.libaray_manager_system.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChapterSplitUtils {
    public record Chapter(String title ,int contentstart , int contentend){}

    private static final Pattern CHAPTER = Pattern.compile(
            "(?m)^[ \\t　]*(" +
                    "第[0-9０-９零一二三四五六七八九十百千万〇两]+[章节回卷篇集]" +
                    "|Chapter\\s+\\d+" +
                    "|序章|楔子|引子|尾声|后记|番外" +
                    ")[^\\n]{0,40}$");

    public static List<Chapter>split(String text){
        List<Chapter> list = new ArrayList<Chapter>();
        if(text == null || text.length()==0) return list;

        List<int[]> marks = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher m = CHAPTER.matcher(text);
        while (m.find()) {
            marks.add(new int[]{m.start(), m.end()});
            titles.add(m.group().trim());
        }

        // 兜底：一个章节标记都没有 → 整篇当一章，绝不报错
        if (marks.isEmpty()) {
            list.add(new Chapter("全文", 0, text.length()));
            return list;
        }

        // 第一个标题前的内容 → 前言
        if (marks.get(0)[0] > 0) {
            String head = text.substring(0, marks.get(0)[0]).trim();
            if (!head.isEmpty()) {
                list.add(new Chapter("前言", 0, marks.get(0)[0]));
            }
        }

        for (int i = 0; i < marks.size(); i++) {
            int titleEnd = marks.get(i)[1];
            int contentStart = titleEnd < text.length() ? titleEnd + 1 : titleEnd;  // 跳过换行
            int end = (i + 1 < marks.size()) ? marks.get(i + 1)[0] : text.length();
            list.add(new Chapter(titles.get(i), contentStart, end));
        }
        return list;
    }
}
