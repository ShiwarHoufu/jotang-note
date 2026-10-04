package com.wlf.note;

import org.springframework.stereotype.Component;

/**
 * 笔记状态迁移：ONLINE / OFFLINE / DELETED，DELETED 为终态。
 * 状态变更集中在此，禁止 Controller 直接改 status。见《概要设计》§4.1。
 */
@Component
public class NoteStateMachine {
}
