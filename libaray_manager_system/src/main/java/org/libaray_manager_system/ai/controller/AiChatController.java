package org.libaray_manager_system.ai.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.libaray_manager_system.ai.entity.dto.AiChatRequest;
import org.libaray_manager_system.ai.service.AiChatService;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.http.HttpResponse;

@RestController
public class AiChatController {
    @Autowired
    private AiChatService aiChatService;

    @PostMapping("/ai/chat")
    public Result chat(@RequestBody AiChatRequest request,
                        @RequestAttribute("loginUser")LoginUser loginUser){
        return new Result().success(aiChatService.chat(request, loginUser));
    }

    @PostMapping(value="/ai/chat/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AiChatRequest request,
                                 @RequestAttribute("loginUser")LoginUser loginUser,
                                 HttpServletResponse response){
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return aiChatService.chatStream(request, loginUser);
    }
}
