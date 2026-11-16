package org.libaray_manager_system.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.libaray_manager_system.ai.config.AiProperties;
import org.libaray_manager_system.ai.entity.AiConversation;
import org.libaray_manager_system.ai.entity.AiMessage;
import org.libaray_manager_system.ai.entity.dto.AiChatRequest;
import org.libaray_manager_system.ai.prompt.LibraryPrompt;
import org.libaray_manager_system.ai.tools.LibraryTools;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.AiConversationMapper;
import org.libaray_manager_system.mapper.AiMessageMapper;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiChatService {

    @Autowired private AiProperties aiProperties;//ai属性对象
    @Value("${spring.ai.deepseek.api-key}") private String apiKey;
    @Autowired private ChatClient chatClient;
    @Autowired private LibraryTools  libraryTools;
    @Autowired private AiConversationMapper aiConversationMapper;
    @Autowired private AiMessageMapper aiMessageMapper;
    /*----------------------聊天方法----------------------*/

    public SseEmitter chatStream(AiChatRequest request, LoginUser loginUser) {
        String content = requestJudge(request);

        //判断是否是新对话
        AiConversation aiConversation = getOrCreate(request.getConversationId(),loginUser,content);
        //加载历史消息
        List<Message> history = loadHistory(aiConversation.getId(),loginUser.getId());
        //保存并提取用户消息
        AiMessage userMessage = saveMessage(aiConversation.getId(), loginUser.getId(), "user", content, null, 1);
        //更新对话最后消息时间
        aiConversationMapper.Touch(aiConversation.getId(),1);

        List<Message> messages = new ArrayList<>(history);
        messages.add(new UserMessage(content));

        SseEmitter emitter = new SseEmitter(aiProperties.getStreamTimeoutMs());
        AtomicBoolean finished = new AtomicBoolean(false);//原子布尔值，用于判断是否完成请求
        AtomicReference<Disposable> subscribe = new AtomicReference<>();//原子引用，disposable用于存储订阅对象
        StringBuilder buffer = new StringBuilder();//字节缓存

        //包装开始消息
        Map<String, Object> meta = new HashMap<>();
        meta.put("conversationId", aiConversation.getId());
        meta.put("userMessageId", userMessage.getId());
        meta.put("model",aiProperties.getModel());
        send(emitter,"meta", meta);

        /** 演示模式,模拟模型回答 */
        if(useMock()) {
            String text = "（演示模式，未调用真实模型）你问的是：「" + content + "」。";
            for(int i = 0;i<text.length();i+=12){
                String piece = text.substring(i, Math.min(i + 12, text.length()));
                send(emitter, "delta", Map.of("text", piece));
            }
            AiMessage mockMsg = saveMessage(aiConversation.getId(), loginUser.getId(), "assistant", text, null, 1);
            aiConversationMapper.Touch(aiConversation.getId(),1);
            finished.set(true);//设置为完成状态

            //包装结束消息
            Map<String, Object>done = new HashMap<>();
            done.put("chars",text.length());//发送完成消息
            done.put("messageId",mockMsg.getId());
            send(emitter,"done", done);

            emitter.complete();
            return emitter;
        }

        /** 真实模型模式,请求模型并返回流 */
        //构建提示词
        ChatClient.ChatClientRequestSpec spec= buildSpec(loginUser,messages);

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
                            AiMessage errorMsg = saveMessage(aiConversation.getId(), loginUser.getId(), "assistant", buffer.toString(), null, 3);
                            if (errorMsg != null) {
                                aiConversationMapper.Touch(aiConversation.getId(), 1);
                            }
                            send(emitter,"error", Map.of("msg",friendly(error)));//发送错误消息
                            emitter.complete();//完成流
                        },
                        () -> {//请求结束
                            if(!finished.compareAndSet(false, true)){
                                return;
                            }
                            AiMessage assistant = saveMessage(aiConversation.getId(), loginUser.getId(), "assistant", buffer.toString(), null, 1);
                            if (assistant != null) {
                                aiConversationMapper.Touch(aiConversation.getId(), 1);
                            }
                            //包装结束消息
                            Map<String, Object>done = new HashMap<>();
                            done.put("chars",buffer.length());//发送完成消息
                            done.put("messageId",assistant==null?null:assistant.getId());

                            send(emitter,"done", done);//发送完成消息
                            emitter.complete();//完成流
                        }
                );
        subscribe.set(disposable);//设置订阅对象
        //设置超时回调
        emitter.onTimeout(() -> {
            if(!finished.compareAndSet(false, true)){return;}
            close(subscribe);//关闭订阅
            //网络超时消息保存
            AiMessage partial = saveMessage(aiConversation.getId(), loginUser.getId(), "assistant", buffer.toString(), null, 2);
            if (partial != null) {
                aiConversationMapper.Touch(aiConversation.getId(), 1);
            }
            emitter.complete();
        });
        //设置完成回调(包含用户中断/关闭页面)
        emitter.onCompletion(() -> {
            if(!finished.compareAndSet(false, true)) return;//正常结束
            close(subscribe);//关闭订阅
            //保存中断消息
            AiMessage interruptMsg = saveMessage(aiConversation.getId(), loginUser.getId(), "assistant", buffer.toString(), null, 2);
            if (interruptMsg != null) {
                aiConversationMapper.Touch(aiConversation.getId(), 1);
            }
        });
        emitter.onError((error) -> close(subscribe));//设置错误回调
        return emitter;
    }
    /*----------------------工具方法----------------------*/
    private boolean useMock(){
        String key = apiKey == null ? "" : apiKey.trim();
        return aiProperties.isMock() || key.isEmpty() || "api-key".equals(key);
    }

    //判断请求是否有效
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
    //封装提示词/提供工具类
     private ChatClient.ChatClientRequestSpec buildSpec(LoginUser loginUser,List<Message> messages){
        ChatClient.ChatClientRequestSpec spec= chatClient.prompt()
                .system(LibraryPrompt.CreaterSystemPrompt(loginUser))
                .messages(messages)
                .toolContext(Map.of("userId",loginUser.getId()));
        if(aiProperties.isToolsEnabled()){
            spec=spec.tools(libraryTools);
        }
        return spec;
    }
    //发送消息
    private void send(SseEmitter emitter, String type, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(type)
                    .data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
                log.debug("发送消息失败",e);
        }
    }
    //格式化错误信息
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
    //关闭模型订阅
    private void close(AtomicReference<Disposable> subscribe){
        Disposable d = subscribe.get();
        if(d != null && !d.isDisposed()){
            d.dispose();//取消订阅
        }
    }
    //判断是否是新对话
    private AiConversation getOrCreate(Long conversationId,LoginUser loginUser,String content){
        //新对话，对话id不存在，创建新对话记录
        if(conversationId == null){
            AiConversation conversation = new AiConversation();
            conversation.setUserId(loginUser.getId());
            conversation.setTitle(titleOf(content));
            conversation.setModel(aiProperties.getModel());
            aiConversationMapper.Insert(conversation);
            return conversation;
        }
        //旧对话，对话id存在，查询会话
        AiConversation conversation = aiConversationMapper.GetById(conversationId, loginUser.getId());
        if(conversation == null){
            throw new BusinessException("会话不存在");
        }
        return conversation;
    }
    //设置会话标题
    private String titleOf(String content){
        String text = content==null?"新对话":content.replaceAll("\\s+"," ").trim();
        return text.length()<=20?text:text.substring(0,20)+"...";
    }
    //加载上下文(不包含系统提示词)
    private List<Message> loadHistory(Long conversationId,Integer userId){
        //从新到旧获取该对话的最近limit条消息
        List<AiMessage> rows = aiMessageMapper.ListRecent(conversationId, userId, aiProperties.getMaxContextMessages());
        List<Message> picked = new ArrayList<>();
        int charlength = 0;
        for(AiMessage row:rows){
            String content = row.getContent();
            if(content == null || content.isBlank()) continue;
            if(!"user".equals(row.getRole()) && !"assistant".equals(row.getRole())) continue;

            charlength += content.length();
            //如果字符数超过预算，停止添加(但是发送上一轮对话记录)
            if(charlength > aiProperties.getMaxContextChars() && !picked.isEmpty()) break;

            picked.add("user".equals(row.getRole())
                    ? new UserMessage(content)
                    : new AssistantMessage(content));
        }
        Collections.reverse(picked);
        while(!picked.isEmpty() && picked.get(0) instanceof AssistantMessage){
            picked.remove(0);
        }
        return picked;
    }
    //保存上下文
    private AiMessage saveMessage(Long conversationId, Integer userId, String role, String content, Usage usage,int status){
        String text = content == null ? "" : content;
        if ("assistant".equals(role) && text.isBlank()) {
            return null;   // 一个字都没生成，不写空消息
        }
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(text);
        message.setPromptTokens(usage == null ? null : usage.getPromptTokens());
        message.setCompletionTokens(usage == null ? null : usage.getCompletionTokens());
        message.setStatus(status);
        aiMessageMapper.Insert(message);
        return message;
    }
}

