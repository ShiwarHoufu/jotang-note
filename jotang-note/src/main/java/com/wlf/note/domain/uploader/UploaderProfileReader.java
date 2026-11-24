package com.wlf.note.domain.uploader;

import java.util.Collection;
import java.util.Map;

/** 上传者资料的跨服务读取端口。 */
public interface UploaderProfileReader {

    /** 按用户 id 批量取；不存在的 id 不出现在结果里。 */
    Map<Long, UploaderProfile> batchGet(Collection<Long> userIds);
}
