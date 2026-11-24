package com.wlf.user.interfaces.internal.assembler;

import com.wlf.user.application.query.BatchUserDisplayQuery;
import com.wlf.user.application.result.UserDisplayResult;
import com.wlf.user.interfaces.internal.dto.InternalUserProfileResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** 内部用户接口的 Query/Result 与 Response 映射。 */
@Component
public class InternalUserAssembler {

    public BatchUserDisplayQuery toQuery(List<Long> ids) {
        return new BatchUserDisplayQuery(ids);
    }

    public InternalUserProfileResponse toResponse(UserDisplayResult result) {
        return new InternalUserProfileResponse(result.id(), result.nickname(),
                result.avatarKey(), result.collegeName());
    }
}
