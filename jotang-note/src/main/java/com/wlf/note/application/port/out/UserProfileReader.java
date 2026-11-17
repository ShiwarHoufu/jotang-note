package com.wlf.note.application.port.out;

import com.wlf.note.application.note.dto.UserProfile;

import java.util.Collection;
import java.util.Map;

/**
 * 上传者展示信息的跨服务端口。note 不再 JOIN user / college。
 */
public interface UserProfileReader {

    /** 按用户 id 批量取；不存在的 id 不出现在结果里。 */
    Map<Long, UserProfile> batchGet(Collection<Long> userIds);
}
