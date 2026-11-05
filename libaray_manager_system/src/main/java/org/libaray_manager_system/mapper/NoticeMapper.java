package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.dto.NoticeSelectDto;
import org.libaray_manager_system.pojo.entity.Notice;
import org.libaray_manager_system.pojo.vo.NoticeDetailVo;
import org.libaray_manager_system.pojo.vo.NoticeVo;

import java.util.List;

@Mapper
public interface NoticeMapper {

    List<NoticeVo> GetNotices(@Param("dto") NoticeSelectDto dto,
                              @Param("userId") Integer userId,
                              @Param("startIndex") Integer startIndex);

    Integer GetNoticeCount(@Param("dto") NoticeSelectDto dto);

    Notice GetNoticeEntityById(@Param("id") Long id);

    NoticeDetailVo GetNoticeById(@Param("id") Long id, @Param("userId") Integer userId);

    void InsertRead(@Param("noticeId") Long noticeId, @Param("userId") Integer userId);

    Integer GetUnreadCount(@Param("userId") Integer userId);

    void Insert(Notice notice);

    void UpdateById(Notice notice);

    void UpdateTop(@Param("id") Long id, @Param("isTop") Integer isTop);

    void DeleteById(@Param("id") Long id);

    void DeleteReadByNoticeId(@Param("noticeId") Long noticeId);

    // 注销用户时清掉他的已读记录
    void DeleteReadByUserId(@Param("userId") Integer userId);
}
