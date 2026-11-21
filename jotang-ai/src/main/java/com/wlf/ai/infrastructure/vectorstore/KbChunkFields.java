package com.wlf.ai.infrastructure.vectorstore;

/** 知识库块在向量库中的 payload 字段。 */
public final class KbChunkFields {

    public static final String DOCUMENT_ID = "document_id";
    public static final String DOCUMENT_PATH = "document_path";
    public static final String TITLE = "title";
    public static final String SOURCE_URL = "source_url";
    public static final String STATUS = "status";
    public static final String STATUS_ACTIVE = "active";
    public static final String CHUNK_INDEX = "chunk_index";
    public static final String TOTAL_CHUNKS = "total_chunks";

    private KbChunkFields() {
    }
}
