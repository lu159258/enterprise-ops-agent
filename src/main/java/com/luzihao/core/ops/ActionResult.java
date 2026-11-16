package com.luzihao.core.ops;

public record ActionResult(boolean success,
                           String mode,             // DRY_RUN / WEBHOOK
                           String detail) {
}
