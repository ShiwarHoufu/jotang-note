package com.wlf.note.domain.uploader;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;

/** 上传者资料缓存端口。未命中的 id 不出现在返回的 Map 里。 */
public interface UploaderProfileCache {

    Map<Long, UploaderProfile> getAll(Collection<Long> userIds);

    void putAll(Map<Long, UploaderProfile> profiles, Duration ttl);
}
