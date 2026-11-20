package com.luzihao.core.diagnose;

import java.util.Map;

/** Planner 输出 JSON 数组中的单个计划项（Jackson 直接反序列化 record，字段名与 JSON 一致，无需注解） */

public record DiagnosePlan(int step, String goal, String tool,
                           Map<String, String> args, String expect) {
}
