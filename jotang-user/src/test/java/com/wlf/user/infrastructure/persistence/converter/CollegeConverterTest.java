package com.wlf.user.infrastructure.persistence.converter;

import com.wlf.user.domain.college.College;
import com.wlf.user.infrastructure.persistence.po.CollegePO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollegeConverterTest {

    @Test
    void mapsPersistedFields() {
        CollegePO po = new CollegePO();
        po.setId(1L);
        po.setName("计算机科学与工程学院");

        College college = new CollegeConverter().toDomain(po);

        assertThat(college.id()).isEqualTo(1L);
        assertThat(college.name()).isEqualTo("计算机科学与工程学院");
    }

    @Test
    void rejectsNullPersistedFields() {
        CollegePO po = new CollegePO();
        po.setId(1L);

        assertThatThrownBy(() -> new CollegeConverter().toDomain(po))
                .isInstanceOf(NullPointerException.class);
    }
}
