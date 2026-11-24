package com.wlf.user.application.query;

import java.util.Collection;

/** 批量查询用户展示信息。 */
public record BatchUserDisplayQuery(Collection<Long> ids) {
}
