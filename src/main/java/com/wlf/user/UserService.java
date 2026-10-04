package com.wlf.user;

import org.springframework.stereotype.Service;

/**
 * 资料维护、头像上传（写公共读 Bucket）、个人主页统计。
 * 其他模块取用户信息也走此 Service，不直接引用 UserMapper。
 */
@Service
public class UserService {
}
