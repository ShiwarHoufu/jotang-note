package com.wlf.ai.infrastructure.note;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * note-service 内部接口的 Feign 客户端。
 *
 * <p>服务名取 Nacos 注册名。
 */
@FeignClient(name = "jotang-note",
        path = "/internal/notes",
        configuration = NoteClientConfig.class)
public interface NoteClient {

    @GetMapping("/{noteId}/file")
    FileInfo requireOwnFile(@PathVariable("noteId") Long noteId,
                            @RequestParam("userId") Long userId);

    @GetMapping("/search")
    List<SearchHit> search(@RequestParam("title") String title,
                           @RequestParam("limit") int limit);

    @GetMapping("/{noteId}/readable-file")
    FileInfo readableOnlineFile(@PathVariable("noteId") Long noteId);

    record FileInfo(String storageKey, Long size, String contentType) {
    }

    record SearchHit(Long noteId, String title, String teacher, String summary) {
    }
}
