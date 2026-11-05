package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.dto.ReaderFileSelectDto;
import org.libaray_manager_system.pojo.entity.ReaderFile;

import java.util.List;

@Mapper
public interface ReaderFileMapper {

    // 插入后用 useGeneratedKeys 把自增 id 回填到 file.id
    int Save(ReaderFile file);

    // 按 id + 归属查（带正文）—— 阅读用；user_id 是防越权的唯一防线
    ReaderFile GetByIdAndUser(@Param("id") Long id, @Param("userId") Integer userId);

    // 列表：故意不查 content（一页 10 个文件可能几十 MB 正文，列表一条都用不上）
    List<ReaderFile> GetMyFiles(@Param("dto") ReaderFileSelectDto dto,
                                @Param("userId") Integer userId,
                                @Param("startIndex") Integer startIndex);

    Integer GetMyFileCount(@Param("dto") ReaderFileSelectDto dto,
                           @Param("userId") Integer userId);

    void DeleteByIdAndUser(@Param("id") Long id, @Param("userId") Integer userId);

    // 只改标题，正文一个字都不动；user_id 同样不能少，否则能改到别人的文件
    int UpdateTitle(@Param("id") Long id,
                    @Param("userId") Integer userId,
                    @Param("title") String title);

    // 注销用户时清掉他上传的全部文件，否则会变成孤儿数据
    int DeleteByUser(@Param("userId") Integer userId);
}
