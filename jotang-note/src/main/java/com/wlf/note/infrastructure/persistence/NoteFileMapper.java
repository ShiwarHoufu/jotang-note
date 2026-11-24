package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * note_file 表的数据访问。单表 CRUD 由 {@link BaseMapper} 提供。
 */
@Mapper
public interface NoteFileMapper extends BaseMapper<NoteFilePO> {
}
