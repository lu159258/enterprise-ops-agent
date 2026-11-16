package com.luzihao.api.dto;

import java.time.Instant;

public record ChatResponse(String sessionId, String answer, Instant timestamp) {
}
