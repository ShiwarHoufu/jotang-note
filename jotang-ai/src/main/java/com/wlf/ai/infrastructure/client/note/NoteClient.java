package com.wlf.ai.infrastructure.client.note;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * note-service 内部接口的 Feign 客户端。
 *
 * <p>阶段 1 先用静态 URL；接 Nacos 后删掉 {@code url}，只留服务名。
 */
@FeignClient(name = "note-service",
        url = "${jotang.clients.note-url:http://localhost:8080}",
        path = "/internal/notes",
        configuration = NoteClientConfig.class)
public interface NoteClient {

    @GetMapping("/{noteId}/file")
    InternalNoteFile requireOwnFile(@PathVariable("noteId") Long noteId,
                                    @RequestParam("userId") Long userId);

    @GetMapping("/search")
    List<InternalNoteSearchHit> search(@RequestParam("title") String title,
                                       @RequestParam("limit") int limit);

    @GetMapping("/{noteId}/readable-file")
    InternalNoteFile readableOnlineFile(@PathVariable("noteId") Long noteId);
}
