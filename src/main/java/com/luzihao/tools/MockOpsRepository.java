package com.luzihao.tools;

import com.luzihao.core.ops.LogEntry;
import com.luzihao.core.ops.OpsRepository;
import com.luzihao.core.ops.ServiceStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 模拟数据源。 */
@Repository
@ConditionalOnProperty(name = "app.ops.mode", havingValue = "mock", matchIfMissing = true)  // 默认装配

public class MockOpsRepository implements OpsRepository {

    private final Map<String, ServiceStatus> services = Map.of(
            "order-service",   status("order-service", "DEGRADED", 3, 78.5, 72.0, 12),
            "user-service",    status("user-service", "UP", 4, 28.0, 41.5, 0),
            "payment-service", status("payment-service", "UP", 2, 35.0, 52.0, 1));

    private final Map<String, List<LogEntry>> logs = Map.of(
            "order-service", List.of(
                    entry(15, "ERROR", "order-service", "数据库连接池等待超时 DB_POOL_TIMEOUT"),
                    entry(15, "ERROR", "order-service", "程序异常，NullPointerException"),
                    entry(13, "WARN",  "order-service", "下游库存服务响应时间升高"),
                    entry(11, "ERROR", "order-service", "创建订单失败，错误码 DB_POOL_TIMEOUT, orderId=10086"),
                    entry(30, "INFO",  "order-service", "服务健康检查通过")),
            "user-service", List.of(
                    entry(8, "INFO", "user-service", "健康检查通过")),
            "payment-service", List.of(
                    entry(20, "WARN", "payment-service", "第三方支付接口响应较慢，耗时 2100ms")));



    /** 造状态数据：结构化指标拼进 detail 文本（统一 record 无指标字段） */
    private ServiceStatus status(String name, String st, int instances,
                                 double cpu, double mem, int errPerMin) {
        return new ServiceStatus(name, st, Instant.now(),
                "instances=%d cpu=%s%% mem=%s%% err/min=%d".formatted(instances, cpu, mem, errPerMin));
    }


    @Override
    public ServiceStatus getServiceStatus(String serviceName) {
        ServiceStatus serviceStatus = services.get(normalize(serviceName));

        return serviceStatus != null ? serviceStatus : new ServiceStatus(serviceName, "UNKNOWN", Instant.now(), "Mock 数据中无此服务");
    }

    @Override
    public List<LogEntry> findLogs(String serviceName, Instant from, Instant to, int limit) {
        List<LogEntry> logEntries = logs.get(normalize(serviceName));
        if (logEntries.isEmpty()){
            return List.of();
        }

        return logEntries.stream().filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to)).limit(limit).toList();
    }






    private LogEntry entry(long minutesAgo, String level, String service, String message) {
        return new LogEntry(Instant.now().minusSeconds(minutesAgo * 60), level, service, message,Map.of());
    }

    public ServiceStatus findService(String name) { return services.get(normalize(name)); }
    public List<LogEntry> findLogs(String name) {
        List<LogEntry> entries = logs.get(normalize(name));
        return entries == null ? List.of() : entries;
    }

    private String normalize(String v) { return v == null ? "" : v.trim().toLowerCase(Locale.ROOT); }
}
