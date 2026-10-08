package com.wlf.note;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.Note;
import org.apache.ibatis.annotations.Mapper;

/**
 * note 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供。列表 / 热门 / 搜索要多表 JOIN
 * （上传者、课程、标签），那些方法走 {@code NoteMapper.xml}
 * ——见《概要设计》§1.1 的跨模块访问约定：检索侧 JOIN 不受「不引用对方实体」的限制。
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {
}
