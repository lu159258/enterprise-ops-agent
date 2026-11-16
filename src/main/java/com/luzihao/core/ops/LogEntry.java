package com.luzihao.core.ops;

import java.time.Instant;
import java.util.Map;

public record LogEntry(Instant timestamp,
                       String level,          // INFO / WARN / ERROR
                       String service,        // 服务名
                       String message,        // 原始消息（脱敏前——脱敏在 LogQueryService 层做，防线位置不变）
                       Map<String, Object> metadata) {
}
