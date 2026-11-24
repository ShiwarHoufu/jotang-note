package com.wlf.user.domain.user.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

/** 用户角色。当前没有公开的改角色行为，ADMIN 只能由预置数据产生。 */
public enum Role {
    USER,
    ADMIN;

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public static Role fromName(String value) {
        for (Role role : values()) {
            if (role.name().equals(value)) {
                return role;
            }
        }
        throw new BusinessException(ErrorCode.SERVER_ERROR, "未知的用户角色: " + value);
    }
}
