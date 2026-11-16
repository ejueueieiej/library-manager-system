package org.libaray_manager_system.ai.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.libaray_manager_system.ai.entity.dto.AiChatRequest;
import org.libaray_manager_system.ai.service.AiChatService;
import org.libaray_manager_system.ai.service.AiConversationService;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;


@RestController
public class AiChatController {
    @Autowired private AiChatService aiChatService;
    @Autowired private AiConversationService aiConversationService;
    //聊天流接口
    @PostMapping(value="/ai/chat/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request,
                                 @RequestAttribute("loginUser")LoginUser loginUser,
                                 HttpServletResponse response){
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return aiChatService.chatStream(request, loginUser);
    }
    //会话管理接口
    //获取用户所有会话
    @GetMapping("/ai/conversations")
    public Result listConversations(@RequestAttribute("loginUser")LoginUser loginUser){
        return new Result().success(aiConversationService.list(loginUser));
    }
    @GetMapping("/ai/conversations/{id}/messages")
    public Result listMessages(@PathVariable Long id,//会话id(conversationId)
                                     @RequestAttribute("loginUser")LoginUser loginUser){
        return new Result().success(aiConversationService.messages(id, loginUser));
    }
    @PutMapping("/ai/conversations/{id}")
    public Result rename(@PathVariable Long id,//会话id(conversationId)
                         @RequestBody Map<String,String> body,//重命名会话名称
                         @RequestAttribute("loginUser")LoginUser loginUser){
        aiConversationService.rename(id, body==null?null:body.get("title"),loginUser);
        return new Result().success();
    }
    @DeleteMapping("/ai/conversations/{id}")
    public Result delete(@PathVariable Long id,//会话id(conversationId)
                         @RequestAttribute("loginUser")LoginUser loginUser){
        aiConversationService.delete(id, loginUser);
        return new Result().success();
    }
}
