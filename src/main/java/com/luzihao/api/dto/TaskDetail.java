package com.luzihao.api.dto;

import com.luzihao.core.diagnose.DiagnoseReport;

import java.util.Date;
import java.util.List;

public record TaskDetail(String taskId, String status, String question, int retryCount,
                         String errorCode, DiagnoseReport report, String resultJson,
                         Date createdAt, Date finishedAt,
                         List<StepView> steps) {
}
