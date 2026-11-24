package com.wlf.note.domain.catalog.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagNameTest {

    @Test
    void trimsAndAcceptsBoundaries() {
        assertThat(new TagName("  期末复习  ").value()).isEqualTo("期末复习");
        assertThat(new TagName("a".repeat(32)).value()).hasSize(32);
    }

    @Test
    void rejectsBlankOrTooLong() {
        assertThatThrownBy(() -> new TagName(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> new TagName("   "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
        assertThatThrownBy(() -> new TagName("a".repeat(33)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAM_INVALID));
    }
}
