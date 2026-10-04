package com.wlf.note;

import org.apache.ibatis.annotations.Mapper;

/**
 * note_tag 关联表的数据访问。
 * 复合主键，不继承 BaseMapper，插入/删除自写 SQL。
 */
@Mapper
public interface NoteTagMapper {
}
