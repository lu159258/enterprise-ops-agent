package com.luzihao.rag;

public record KnowledgeHit(String evidenceId,
                           String documentId,
                           String title,
                           String snippet,       // 命中片段（截断）
                           String sourceUri,
                           String permissionTag,
                           double score) {
}
