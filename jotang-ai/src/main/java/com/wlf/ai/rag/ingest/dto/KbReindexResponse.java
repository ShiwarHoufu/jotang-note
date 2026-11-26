package com.wlf.ai.rag.ingest.dto;

import java.util.List;

/** 知识库重建索引的结果。 */
public record KbReindexResponse(int total, int indexed, int skipped, List<String> failed) {
}
