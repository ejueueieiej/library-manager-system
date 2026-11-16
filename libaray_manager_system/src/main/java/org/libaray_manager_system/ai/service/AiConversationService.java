package org.libaray_manager_system.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.ai.config.AiProperties;
import org.libaray_manager_system.ai.entity.AiConversation;
import org.libaray_manager_system.ai.entity.AiMessage;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.AiConversationMapper;
import org.libaray_manager_system.mapper.AiMessageMapper;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AiConversationService {
    @Autowired private AiMessageMapper aiMessageMapper;
    @Autowired private AiProperties aiProperties;
    @Autowired private AiConversationMapper aiConversationMapper;

    //获取用户所有会话
    public List<AiConversation> list(LoginUser loginUser) {
        return aiConversationMapper.ListByUser(loginUser.getId());
    }

    //获取会话所有消息
    public List<AiMessage> messages(Long id, LoginUser loginUser) {
        checkIdAndUser(id, loginUser);
        return aiMessageMapper.ListByConversation(id, loginUser.getId());
    }

    //重命名会话
    public void rename(Long id, String title, LoginUser loginUser) {
        checkIdAndUser(id, loginUser);
        aiConversationMapper.UpdateTitle(id, loginUser.getId(), normalizeTitle(title));
    }
    //删除会话
    @Transactional
    public void delete(Long id, LoginUser loginUser) {
        checkIdAndUser(id, loginUser);
        //先删除消息
        aiMessageMapper.DeleteByConversation(id, loginUser.getId());
        //再删除会话
        aiConversationMapper.DeleteById(id, loginUser.getId());
    }

    private String normalizeTitle(String title) {
        String text = title==null?"":title.trim();
        if(text.isEmpty()){
            return "新对话";
        }
        return text;
    }

    private void checkIdAndUser(Long id, LoginUser loginUser){
        if(aiConversationMapper.GetById(id, loginUser.getId())==null){
            throw new BusinessException("会话不存在");
        }
    }



}
