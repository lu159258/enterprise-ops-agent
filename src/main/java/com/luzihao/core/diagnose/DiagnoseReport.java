package com.luzihao.core.diagnose;

import java.util.List;

public record DiagnoseReport(String summary,                          // 问题摘要
                             List<PossibleCause> possibleCauses,      // 可能原因（带证据与置信）
                             List<String> recommendedActions,         // 建议动作
                             List<String> limitations) {

    public record PossibleCause(
            String cause,
            List<String> evidence,               // 必须引用 evidenceId（doc-x / log-x）
            String confidence) {}
}
