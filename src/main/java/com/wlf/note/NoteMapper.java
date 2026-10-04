package com.wlf.note;

import org.apache.ibatis.annotations.Mapper;

/**
 * note 表的数据访问。
 * 列表/热门/搜索的多表 join 走 NoteMapper.xml（见《概要设计》§1.1 跨模块访问约定）。
 */
@Mapper
public interface NoteMapper {
}
