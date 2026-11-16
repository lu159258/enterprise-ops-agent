package com.luzihao.api.dto;

import com.luzihao.infra.entity.AgentTaskStep;

import java.util.Date;

public record StepView(int stepNo, String stepType, String toolName, String status,
                       String inputSummary, String outputSummary, String errorCode,
                       Date startedAt, Date finishedAt) {
   public static StepView from(AgentTaskStep s) {
        return new StepView(s.getStepNo(), s.getStepType(), s.getToolName(), s.getStatus(),
                s.getInputSummary(), s.getOutputSummary(), s.getErrorCode(),
                s.getStartedAt(), s.getFinishedAt());
    }
}
