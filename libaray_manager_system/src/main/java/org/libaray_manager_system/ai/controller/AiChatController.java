package org.libaray_manager_system.ai.controller;

import org.libaray_manager_system.ai.entity.dto.AiChatRequest;
import org.libaray_manager_system.ai.service.AiChatService;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiChatController {
    @Autowired
    private AiChatService aiChatService;

    @PostMapping("/ai/chat")
    public Result chat(@RequestBody AiChatRequest request,
                        @RequestAttribute("loginUser")LoginUser loginUser){
        return new Result().success(aiChatService.chat(request, loginUser));
    }
}
