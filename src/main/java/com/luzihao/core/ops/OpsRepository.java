package com.luzihao.core.ops;

import java.time.Instant;
import java.util.List;

/** 运维数据源：服务状态 + 日志查询（覆盖 MockOpsRepository 现有两个方法） */

public interface OpsRepository {
    ServiceStatus getServiceStatus(String serviceName);
    List<LogEntry> findLogs(String serviceName, Instant from,Instant to,int limit);

}
