package com.luzihao.tools;

import com.luzihao.core.ops.LogEntry;
import com.luzihao.core.ops.ServiceStatus;
import com.luzihao.core.user.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LogQueryService {

    /** 日志脱敏：密码/token/密钥类键值对打码。真实系统还会有更多规则。 */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(password|passwd|token|secret|api[_-]?key)\\s*[=:]\\s*\\S+");

    private static final Set<String> TIME_RANGES = Set.of("1h","24h","7d");

    @Autowired
    private MockOpsRepository mockOpsRepository;

    @Value("${app.tools.service-whitelist}")
    List<String> whitelist;

    @Value("${app.tools.log-max-rows:20}")
    int maxRows;

    /** 服务状态查询（白名单 + 用户授权） */
    public String queryServiceStatus(UserInfo userInfo, String serviceName){
        String name = normalize(serviceName);
        if (!whitelist.contains(name)){
            return "查询失败，服务不在白名单。可查询:"+String.join(",",whitelist);
        }

        if (!userInfo.getAllowedServices().contains(name)){
            return "查询失败，用户"+userInfo.getUserId()+"无权访问服务"+name;
        }


        ServiceStatus service = mockOpsRepository.findService(name);
        if (null == service){
            return "未找到该服务"+name;
        }

        return """
                服务：%s
                状态：%s
                检测时间：%s
                详情：%s
             
                """.formatted(service.service(), service.status(), service.checkedAt(), service.detail());
    }

    private String normalize(String v) { return v == null ? "" : v.trim().toLowerCase(Locale.ROOT); }

    /** 设计文档签名：searchLogs(serviceName, keyword, timeRange)。白名单只读 + 脱敏 + 条数上限 */
    public String searchLogs(UserInfo userInfo,String serviceName,String keyword,String timeRange){
        String name = normalize(serviceName);
        if (!whitelist.contains(name))
            return "查询失败：服务不在白名单。可查询：" + String.join(", ", whitelist);
        if (!userInfo.getAllowedServices().contains(name))
            return "查询失败：用户 " + userInfo.getUserId() + " 无权访问服务 " + name;

        String range = StringUtils.hasText(timeRange) ? timeRange.trim().toLowerCase(Locale.ROOT) : "24h";
        if (!TIME_RANGES.contains(range)){
            return "查询失败：timeRange 仅支持 " + TIME_RANGES;

        }

        String kw = keyword == null ? "" : keyword.trim();
        if (kw.length() > 100){
            return "查询失败：keyword 过长（上限 100 字符）";
        }

        List<LogEntry> entries = mockOpsRepository.findLogs(name).stream()
                .filter(e -> kw.isEmpty() || e.message().toLowerCase(Locale.ROOT)
                        .contains(kw.toLowerCase(Locale.ROOT))
                ).filter(e -> inRange(e, range))
                .limit(maxRows)
                .toList();

        if (entries.isEmpty()){
            return "该服务在 " + range + " 内没有匹配日志（keyword=" + kw + "）。";

        }

        StringBuilder out = new StringBuilder("日志（最多 %d 条）：\n".formatted(maxRows));
        for (LogEntry e : entries) {
            out.append("[%s] [%s] %s\n".formatted(e.timestamp(), e.level(), desensitize(e.message())));
        }
        return out.toString();
    }


    private String desensitize(String message) {
        return SENSITIVE.matcher(message).replaceAll("$1=***");
    }

    private boolean inRange(LogEntry e, String range) {
        long minutes = switch (range) { case "1h" -> 60; case "24h" -> 60 * 24; default -> 60 * 24 * 7; };
        return e.timestamp().isAfter(OffsetDateTime.now().minusMinutes(minutes).toInstant());
    }

}
