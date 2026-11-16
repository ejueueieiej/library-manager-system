package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiQueryMapper {
    // 查询书籍
    List<Map<String,Object>> SearchBooks(@Param("kw")String kw,
                                         @Param("limit")int limit);

    // 查询用户当前借阅记录
    List<Map<String,Object>> ListActiveBorrows(@Param("userId")int userId,
                                              @Param("limit")int limit);
}
