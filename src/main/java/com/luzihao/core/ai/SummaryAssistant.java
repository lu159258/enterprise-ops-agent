package com.luzihao.core.ai;

import com.luzihao.core.diagnose.DiagnoseReport;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SummaryAssistant {
    @SystemMessage("""
        你是运维诊断报告汇总专家。根据用户原始问题和各子计划的执行结果，
        汇总为一份结构化诊断报告：问题结论、关键证据（引用 evidenceId）、处置建议。
        """)
    DiagnoseReport summarize(@UserMessage String question, @V("results") String subResults);
}
