package org.libaray_manager_system.ai.service;

import org.libaray_manager_system.ai.config.AiProperties;
import org.libaray_manager_system.ai.entity.dto.AiChatRequest;
import org.libaray_manager_system.ai.entity.vo.AiChatVo;
import org.libaray_manager_system.ai.prompt.LibraryPrompt;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
    @Autowired private AiProperties aiProperties;//ai属性对象
    @Value("${spring.ai.deepseek.api-key}") private String apiKey;
    @Autowired
    private ChatClient chatClient;

    //聊天
    public AiChatVo chat(AiChatRequest request, LoginUser loginUser) {
        if (!aiProperties.isEnabled()) {
            throw new BusinessException("ai功能未开启");
        }

        String content = (request == null || request.getContent() == null) ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException("请输入问题");
        }
        if (content.length() > 2000) {
            throw new BusinessException("请将字数控制在2000字以内");
        }

        Long start = System.currentTimeMillis();

        if (useMock()){
            String context = "（演示模式，未调用真实模型）你问的是：「" + content + "」。"
                    + "配置 DEEPSEEK_API_KEY 后重启即可获得真实回答。";
            return new AiChatVo(context,aiProperties.getModel(),null,null,System.currentTimeMillis()-start,true);
        }

        ChatResponse response = chatClient.prompt()//开始组建提示词
                .system(LibraryPrompt.CreaterSystemPrompt(loginUser))//系统提示词
                .user(content)//用户提示词
                .call().chatResponse();//请求模型并包装成chatresponse对象

        String text = response == null ? "":
                response
                .getResult()
                .getOutput()
                .getText();
        Usage usage = response.getMetadata().getUsage();

        return new AiChatVo(
                text,
                aiProperties.getModel(),
                usage == null? null :usage.getPromptTokens(),
                usage == null? null :usage.getCompletionTokens(),
                System.currentTimeMillis()-start,
                false);

    }

    private boolean useMock(){
        if(aiProperties.isMock()|| apiKey.equals("api-key") || apiKey == null){
            return true;
        }
        return false;
    }
}
