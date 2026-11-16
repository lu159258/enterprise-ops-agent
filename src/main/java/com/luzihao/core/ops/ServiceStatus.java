package com.luzihao.core.ops;

import java.time.Instant;

public record ServiceStatus(
        String service,
        String status,         // UP / DOWN / UNKNOWN
        Instant checkedAt,
        String detail
) {
}
