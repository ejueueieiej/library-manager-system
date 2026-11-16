package org.libaray_manager_system.ai.tools;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.mapper.AiQueryMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class LibraryTools {

    private final AiQueryMapper aiQueryMapper;
    private final ObjectMapper objectMapper;

    @Tool(description = """
            按关键词检索馆藏图书（书名、作者或 ISBN 的片段），最多返回 5 条。
            当用户问「有没有某本书」「找某作者的书」「这本书还有没有货」时调用。
            """)
    public String SearchBooks(@ToolParam(description = "书名 / 作者 / ISBN 的任意片段，不要带书名号")
                                  String keyword)
    {
        String kw = keyword==null?"":keyword.trim();
        if(kw.isEmpty()){
            return "[]";
        }
        return ToJson(aiQueryMapper.SearchBooks(kw,5));
    }


    @Tool(description = """
            查询当前登录用户正在借阅（未归还）的图书、借阅时间、到期时间、是否逾期，最多 10 条。
            当用户问「我借了什么」「哪本书快到期」「我有没有逾期」时调用。
            """)
    //toolcontext用于储存不方便直接发送给模型的敏感信息，如用户ID等
    public String MyBorrows(ToolContext context) {
        Object value = context==null?"":context.getContext().get("userId");
        if(!(value instanceof Integer userId)){
            return "无法获取当前用户";
        }
        return ToJson(aiQueryMapper.ListActiveBorrows(userId,10));
    }

    public String ToJson(Object obj){
        try{
            String json = objectMapper.writeValueAsString(obj);
            return json.length()>2000?json.substring(0,2000):json;
        }catch(JacksonException e){
            log.warn("结果转化失败",e);
            return "查询失败";
        }
    }
}
