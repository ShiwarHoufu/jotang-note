package com.wlf.user.domain.user.model;

/** 账号状态，数据库存 1 / 0。 */
public enum UserStatus {

    ACTIVE(1),
    DISABLED(0);

    private final int code;

    UserStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static UserStatus fromCode(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw UserViolation.serverError("未知的用户状态: " + code);
    }
}
