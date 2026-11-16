package com.luzihao.core.ops;
/** 动作执行器：审批通过后的动作落地（dry-run / webhook 双模式由实现类决定） */

public interface ActionExecutor {
    ActionResult execute(ActionRequest request);
}
