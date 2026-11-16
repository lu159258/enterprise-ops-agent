package com.luzihao.api.dto;

import com.luzihao.rag.KnowledgeHit;

import java.util.List;

public record KnowledgeSearchResponse(String query, List<KnowledgeHit> hits) {
}
