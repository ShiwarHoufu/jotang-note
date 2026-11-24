package com.wlf.note.config;

import com.wlf.note.domain.note.NoteFilePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 领域对象的装配。 */
@Configuration
public class NoteDomainConfig {

    @Bean
    public NoteFilePolicy noteFilePolicy() {
        return new NoteFilePolicy();
    }
}
