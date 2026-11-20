package com.luzihao.core.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@Slf4j
public class WebhookNotificationChannel implements NotificationChannel{
    @Value("${app.actions.webhook-url}")
    private String webhookUrl;

    private final RestClient restClient = RestClient.create();

    @Override
    public void send(Notification notification) {
        if (!StringUtils.hasText(webhookUrl)) return;    // 未配置 = 静默关闭，不报错

        try {
            restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)

                    .body(Map.of("msg_type", "text",
                            "content", "[%s] 任务 %s(%s): %s".formatted(
                                    notification.type(), notification.taskId(), notification.serviceName(), notification.summary())))
                    .retrieve().toBodilessEntity();
        }catch (Exception e){
            log.error("通知发送失败（不影响主流程）taskId={}", notification.taskId(), e);  // 通知失败绝不抛出

        }
    }
}
