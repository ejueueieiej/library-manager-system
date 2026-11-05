package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.entity.ReaderProgress;

@Mapper
public interface ReaderProgressMapper {

    // targetType：1=书籍 2=用户上传的文件；targetId 是 Long（要装得下 reader_file.id）
    ReaderProgress Get(@Param("userId") Integer userId,
                       @Param("targetType") Integer targetType,
                       @Param("targetId") Long targetId);

    // 靠唯一索引 uk_progress(user_id, target_type, target_id) 做 UPSERT
    int Save(ReaderProgress progress);

    // 正文被删时，连带清掉所有人对它的进度（所以故意不带 userId）
    void DeleteByTarget(@Param("targetType") Integer targetType,
                        @Param("targetId") Long targetId);

    // 注销用户时清掉他的全部阅读进度
    int DeleteByUser(@Param("userId") Integer userId);
}
