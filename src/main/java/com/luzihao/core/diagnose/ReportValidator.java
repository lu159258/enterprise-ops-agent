package com.luzihao.core.diagnose;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ReportValidator {
    private static final Set<String> CONFIDENCE = Set.of("high", "medium", "low");


    public DiagnoseReport validate(DiagnoseReport report,EvidenceBundle bundle){
        if (null == report || !StringUtils.hasText(report.summary())){
            throw new IllegalStateException("报告缺少 summary");
        }

        Set<String> validIds = Set.copyOf(bundle.ids());
        List<String> limitations = new ArrayList<>(report.limitations() == null ? List.of() : report.limitations());

        int removed = 0;

        List<DiagnoseReport.PossibleCause> causes = new ArrayList<>();
        if (report.possibleCauses() != null){
            for (DiagnoseReport.PossibleCause possibleCause : report.possibleCauses()) {
                List<String> evidence = possibleCause.evidence();
                List<String> kept = evidence.stream().filter(validIds::contains).toList();
                removed += evidence.size() - kept.size();
                String confidence = CONFIDENCE.contains(String.valueOf(possibleCause.confidence())) ? possibleCause.confidence() : "low";

                causes.add(new DiagnoseReport.PossibleCause(possibleCause.cause(), kept,confidence));
            }
        }


        if (removed > 0){
            limitations.add("已剔除 " + removed + " 个无效证据引用");
        }

        if (causes.isEmpty()){
            limitations.add("未能基于现有证据确定可能原因");
        }

        return new DiagnoseReport(report.summary(),causes,report.recommendedActions(),limitations);
    }
}
