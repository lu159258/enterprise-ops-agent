package com.luzihao.core.ops.adapter;

import com.luzihao.core.ops.ActionExecutor;
import com.luzihao.core.ops.ActionRequest;
import com.luzihao.core.ops.ActionResult;
import com.luzihao.infra.entity.ActionExecution;
import com.luzihao.infra.service.ActionExecutionService;
import com.luzihao.utils.KeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.actions.mode",havingValue = "webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookActionExecutor implements ActionExecutor {

    @Autowired
    private ActionExecutionService actionExecutionService;


    private final RestClient restClient = RestClient.create();
    @Value("${app.actions.webhook-url:}")
    private String webhookUrl;

    @Override
    public ActionResult execute(ActionRequest request) {
        ActionResult actionResult = doWebhook(request);
        saveAudit(request,actionResult);
        return actionResult;
    }

    private ActionResult doWebhook(ActionRequest req){
        if (!StringUtils.hasText(webhookUrl)){
            return new ActionResult(false, "WEBHOOK", "未配置 app.actions.webhook-url");

        }

        try {
            Map<String, Object> body = Map.of(
                    "action", req.action(),
                    "serviceName", req.serviceName(),
                    "taskId", req.taskId(),
                    "approverId", req.approverId());

          restClient.post().uri(webhookUrl)
                  .contentType(MediaType.APPLICATION_JSON)
                  .header("X-Idempotency-Key",req.idempotencyKey())
                  .body(body)
                  .retrieve().toBodilessEntity();
          return new ActionResult(true,"WEBHOOK","已委托运维平台执行动作 %s@%s".formatted(req.action(), req.serviceName()));


        }catch (Exception e){
            log.error("Webhook 动作执行失败 action={} service={}（不中断主流程）",
                    req.action(), req.serviceName(), e);
            return new ActionResult(false, "WEBHOOK", "执行失败：" + e.getMessage());
        }
    }


    private void saveAudit(ActionRequest req, ActionResult result) {
        ActionExecution e = new ActionExecution();
        e.setId(KeyUtils.get32UUID());                 // 项目现有工具类（com.luzihao.utils.KeyUtils）
        e.setTaskId(req.taskId());
        e.setApproverId(req.approverId());
        e.setAction(req.action());
        e.setServiceName(req.serviceName());
        e.setMode(result.mode());
        e.setResult(result.success() ? "SUCCESS" : "FAILED");
        e.setDetail(result.detail());
        e.setIdempotencyKey(req.idempotencyKey());
        actionExecutionService.save(e);
    }
}
