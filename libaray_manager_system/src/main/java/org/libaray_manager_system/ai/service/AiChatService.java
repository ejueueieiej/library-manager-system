package org.libaray_manager_system.ai.service;

import lombok.extern.slf4j.Slf4j;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiChatService {
    @Autowired private AiProperties aiProperties;//ai属性对象
    @Value("${spring.ai.deepseek.api-key}") private String apiKey;
    @Autowired
    private ChatClient chatClient;

    //聊天
    public AiChatVo chat(AiChatRequest request, LoginUser loginUser) {
        String content = requestJudge(request);

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
        String key = apiKey == null ? "" : apiKey.trim();
        return aiProperties.isMock() || key.isEmpty() || "api-key".equals(key);
    }

    public SseEmitter chatStream(AiChatRequest request, LoginUser loginUser) {
        String content = requestJudge(request);

        SseEmitter emitter = new SseEmitter(aiProperties.getStreamTimeoutMs());
        AtomicBoolean finished = new AtomicBoolean(false);//原子布尔值，用于判断是否完成请求
        AtomicReference<Disposable> subscribe = new AtomicReference<>();//原子引用，用于存储订阅对象
        StringBuilder buffer = new StringBuilder();//字节缓存

        send(emitter,"meta", Map.of("model",aiProperties.getModel()));

        /** 演示模式,模拟模型回答 */
        if(useMock()) {
            String text = "（演示模式，未调用真实模型）你问的是：「" + content + "」。";
            for(int i = 0;i<text.length();i+=12){
                String piece = text.substring(i, Math.min(i + 12, text.length()));
                send(emitter, "delta", Map.of("text", piece));
            }
            finished.set(true);//设置为完成状态
            send(emitter,"done", Map.of("chars",text.length()));//发送完成消息
            emitter.complete();
            return emitter;
        }

        /** 真实模型模式,请求模型并返回流 */
        //构建提示词
        ChatClient.ChatClientRequestSpec spec= chatClient.prompt()
                .system(LibraryPrompt.CreaterSystemPrompt(loginUser))
                .user(content);

        Disposable disposable = spec.stream()
                .content()
                .subscribe(
                        chunk -> {//请求成功
                            if(chunk.isEmpty()){
                                return;
                            }
                            buffer.append(chunk);
                            send(emitter,"delta", Map.of("text",chunk));
                        },
                        error -> {//请求失败
                            if(!finished.compareAndSet(false, true)){
                                return;
                            }
                            log.warn("请求模型失败",error);
                            send(emitter,"error", Map.of("msg",friendly(error)));//发送错误消息
                            emitter.complete();//完成流
                        },
                        () -> {//请求结束
                            if(!finished.compareAndSet(false, true)){
                                return;
                            }
                            send(emitter,"done", Map.of("chars",buffer.length()));//发送完成消息
                            emitter.complete();//完成流
                        }
                );
        subscribe.set(disposable);//设置订阅对象

        emitter.onTimeout(() -> {
            emitter.complete();
            close(finished, subscribe);//关闭订阅
        });//设置超时回调
        emitter.onCompletion(() -> close(finished, subscribe));//设置完成回调
        emitter.onError((error) -> close(finished, subscribe));//设置错误回调
        return emitter;
    }

    private String requestJudge(AiChatRequest request) {
        if(!aiProperties.isEnabled()){
            throw new BusinessException("ai功能未开启");
        }
        String content = (request == null || request.getContent() == null) ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException("请输入问题");
        }
        if (content.length() > 2000) {
            throw new BusinessException("请将字数控制在2000字以内");
        }
        return content;
    }

    private void send(SseEmitter emitter, String type, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(type)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
                log.debug("发送消息失败",e);
        }
    }

    private String friendly(Throwable error){
        String raw = error.getMessage() == null ? "" : error.getMessage();
        String lowerCase = raw.toLowerCase();
        if(lowerCase.contains("401") || lowerCase.contains("unauthorized")){
            return "api密钥错误";
        }
        if(lowerCase.contains("429") || lowerCase.contains("rate limit")){
            return "请求频率过快，请稍后重试";
        }
        if(lowerCase.contains("500") || lowerCase.contains("internal server error")){
            return "deepseek官方服务器繁忙，请稍后重试";
        }
        return "未知错误，AI服务暂时不可用，请稍后重试";
    }

    private void close(AtomicBoolean finished, AtomicReference<Disposable> subscribe){
        if(!finished.compareAndSet(false, true)){return;}
        Disposable d = subscribe.get();
        if(d != null && !d.isDisposed()){
            d.dispose();//取消订阅
        }
    }
}

