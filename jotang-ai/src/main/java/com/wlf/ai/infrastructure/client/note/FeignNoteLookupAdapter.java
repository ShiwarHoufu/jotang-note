package com.wlf.ai.infrastructure.client.note;

import com.wlf.ai.application.port.out.NoteLookupPort;
import com.wlf.ai.application.port.out.NoteSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;

/** 把 note 的标题检索接口适配成 NoteLookupPort。 */
@Component
public class FeignNoteLookupAdapter implements NoteLookupPort {

    private final NoteClient noteClient;

    public FeignNoteLookupAdapter(NoteClient noteClient) {
        this.noteClient = noteClient;
    }

    @Override
    public List<NoteSearchHit> findOnlineByExactTitle(String title, int limit) {
        return noteClient.search(title, limit).stream()
                .map(hit -> new NoteSearchHit(hit.noteId(), hit.title(), hit.teacher(), hit.summary()))
                .toList();
    }
}
