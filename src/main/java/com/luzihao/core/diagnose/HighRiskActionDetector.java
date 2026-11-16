package com.luzihao.core.diagnose;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 识别报告建议中的高风险动作。规则保持简单透明，便于审计。 */

@Component
public class HighRiskActionDetector {
    private static final List<String> RISK_KEYWORDS = List.of("重启", "restart", "发布", "部署", "缩容", "扩容","ERROR","增加","评估");
    public boolean containsHighRiskAction(DiagnoseReport report) {
        if (report.recommendedActions() == null) return false;
        return report.recommendedActions().stream()
                .map(a -> a.toLowerCase(Locale.ROOT))
                .anyMatch(a -> RISK_KEYWORDS.stream().anyMatch(a::contains));
    }
}
