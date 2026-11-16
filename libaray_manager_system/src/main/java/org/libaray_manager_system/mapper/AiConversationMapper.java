package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.ai.entity.AiConversation;

import java.util.List;

@Mapper
public interface AiConversationMapper {
    /** 插入会话 */
    int Insert(AiConversation conversation);
    /** 按会话id查询会话 */
    AiConversation GetById(@Param("id") Long id , @Param("userId") Integer userId);
    /** 按用户id查询会话 */
    List<AiConversation> ListByUser(@Param("userId") Integer userId);
    /** 更新会话 标题*/
    int UpdateTitle(@Param("id") Long id , @Param("userId") Integer userId, @Param("title") String title);
    /*消息数+count并刷新时间*/
    int Touch(@Param("id") Long id , @Param("count") int count);
    /** 删除会话 ，硬删除，调用之前先删消息*/
    int DeleteById(@Param("id") Long id,@Param("userId") Integer userId);
}
