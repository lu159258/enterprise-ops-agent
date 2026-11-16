package com.luzihao.api.dto;

public record DiagnoseStartResponse(String taskId, String status, boolean idempotent) {
}
