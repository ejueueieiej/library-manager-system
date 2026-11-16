package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.libaray_manager_system.ai.entity.AiMessage;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface AiMessageMapper {
    /** 插入消息 */
    int Insert(AiMessage aiMessage);
    /*查一个用户最近limit条消息*/
    List<AiMessage> ListRecent(@Param("conversationId") Long conversationId, @Param("userId") Integer userId, @Param("limit") int limit);
    /** 查询一个对话的所有消息 */
    List<AiMessage> ListByConversation(@Param("conversationId") Long conversationId, @Param("userId") Integer userId);
    /*硬删除会话时先删除消息表中的消息*/
    int DeleteByConversation(@Param("conversationId") Long conversationId, @Param("userId") Integer userId);
}
