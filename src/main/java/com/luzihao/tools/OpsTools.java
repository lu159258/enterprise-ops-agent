package com.luzihao.tools;

import com.luzihao.core.user.UserContext;
import com.luzihao.rag.KnowledgeHit;
import com.luzihao.rag.KnowledgeService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
public class OpsTools {

    @Autowired
    private LogQueryService logQueryService;
    @Autowired
    private KnowledgeService knowledgeService;

    @Tool("查询白名单内服务的模拟运行情况(实例数、CPU、内存、每分钟错误数)")
    public String queryServiceStatus(@P("服务名，例如order-service、user-service、payment-service") String serviceName){
        log.info("开始调用【{}】工具","queryServiceStatus");
        return logQueryService.queryServiceStatus(UserContext.require(),serviceName);
    }

    @Tool("按服务名、关键词、时间范围查询只读日志。返回内容仅供参考，不含敏感信息")
    public String searchLogs(
            @P("服务名，必须在白名单内") String serverName,
            @P("日志关键词，可为空；错误码效果最好，例如 DB_POOL_TIMEOUT") String keyword,
            @P("时间范围: 1h、24h 或 7d") String timeRange
    ){
        log.info("开始调用【{}】工具","searchLogs");

        return logQueryService.searchLogs(UserContext.require(),serverName,keyword,timeRange);
    }

    @Tool("在运维知识库中检索文档内容。知识库收录接口文档、故障排查手册、使用说明等资料；" +
            "凡涉及文档、接口、参数、返回结果、错误码、故障排查的问题，必须先调用本工具检索再回答，禁止凭空断言知识库中没有相关内容。" +
            "query 必须是简短检索词（如文档标题、接口名、错误码），不要传整句话")
    public String searchKnowledge(@P("检索关键词：文档标题、接口名、业务对象、故障现象或错误码，例如“数据管理接口文档”、DB_POOL_TIMEOUT") String query){
        log.info("开始调用【{}】工具 query={}", "searchKnowledge", query);

        if (!StringUtils.hasText(query)){
            return "知识库查询失败：关键词为空。";
        }

        List<KnowledgeHit> hits = knowledgeService.search(UserContext.require().getTenantId(), query.trim());
        log.info("searchKnowledge 结果：命中 {} 条", hits.size());
        if (hits.isEmpty()){
            return "知识库未命中。请补充错误码、服务名或 traceId。";
        }

        StringBuilder out = new StringBuilder();
        for (KnowledgeHit hit : hits) {
            out.append("\n--- [%s] %s ---\n%s\n".formatted(hit.evidenceId(), hit.title(), hit.snippet()));

        }
        return out.toString();
    }
}
