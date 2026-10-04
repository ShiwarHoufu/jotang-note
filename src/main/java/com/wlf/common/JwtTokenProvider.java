package com.wlf.common;

import org.springframework.stereotype.Component;

/**
 * JWT 签发与校验（HS256，密钥走环境变量，TTL 2 小时）。
 * 见《概要设计》§6.4。
 */
@Component
public class JwtTokenProvider {
}
