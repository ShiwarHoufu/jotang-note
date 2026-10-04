package com.wlf.admin;

import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员笔记下架 / 恢复接口，仅 role=ADMIN 可访问。
 * 只改 status，不动 OSS 对象。见《概要设计》§5.6、§6.1。
 */
@RestController
public class AdminNoteController {
}
