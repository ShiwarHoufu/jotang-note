package com.wlf.user.infrastructure.persistence.converter;

import com.wlf.user.domain.college.College;
import com.wlf.user.infrastructure.persistence.po.CollegePO;
import org.springframework.stereotype.Component;

/** CollegePO 与领域实体之间的转换。 */
@Component
public class CollegeConverter {

    public College toDomain(CollegePO po) {
        return College.reconstitute(po.getId(), po.getName());
    }
}
