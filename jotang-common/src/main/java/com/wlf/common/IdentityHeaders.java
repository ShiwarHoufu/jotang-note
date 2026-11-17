package com.wlf.common;

/**
 * 网关向下游透传身份的请求头。
 *
 * <p>只传 userId 与 role，不传用户名：HTTP 头按 ISO-8859-1 传输，中文昵称会坏；
 * 需要展示名时由服务去 user-service 取。
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    /** 网关身份头的防伪凭证，客户端同名头会被网关剥掉 */
    public static final String GATEWAY_TOKEN = "X-Gateway-Token";
    /** 服务间内部接口令牌，网关一律剥掉外部请求里的同名头 */
    public static final String INTERNAL_TOKEN = "X-Internal-Token";

    private IdentityHeaders() {
    }
}
