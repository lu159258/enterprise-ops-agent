package com.luzihao.core.diagnose;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luzihao.core.ai.DiagnoseAssistant;
import com.luzihao.core.ai.PlannerAssistant;
import com.luzihao.core.ai.SummaryAssistant;
import com.luzihao.core.ops.OpsRepository;
import com.luzihao.core.ops.ServiceStatus;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.entity.AgentTaskStep;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.SysRole;
import com.luzihao.infra.mapper.SysRoleMapper;
import com.luzihao.infra.service.AgentTaskService;
import com.luzihao.infra.service.AgentTaskStepService;
import com.luzihao.infra.service.AppUserService;
import com.luzihao.rag.KnowledgeHit;
import com.luzihao.rag.KnowledgeService;
import com.luzihao.tools.LogQueryService;
import com.luzihao.utils.KeyUtils;
import dev.langchain4j.internal.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

@Component("diagnoseExecutorService")
@Slf4j
public class DiagnoseExecutor {
    @Autowired
    private AgentTaskStepService agentTaskStepService;
    @Autowired
    private AgentTaskService agentTaskService;
    @Autowired
    private KnowledgeService knowledgeService;
    @Autowired
    private LogQueryService logQueryService;
    @Autowired
    private DiagnoseAssistant diagnoseAssistant;
    @Autowired
    private ReportValidator reportValidator;
    @Autowired
    private HighRiskActionDetector highRiskActionDetector;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private AppUserService appUserService;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Value("${app.diagnose.max-duration-seconds:120}")
    long maxDurationSeconds;
    @Value("${app.diagnose.model-retry-max-attempts:3}")
    int retryMaxAttempts;
    @Value("${app.diagnose.model-retry-backoff-ms:1000}")
    long retryBackoffMs;

    @Autowired
    private PlannerAssistant plannerAssistant;
    @Autowired
    private SummaryAssistant summaryAssistant;
    @Autowired
    private OpsRepository opsRepository;
    @Value("${app.planner.enabled}")
    private boolean plannerEnabled;
    private static final Set<String> PLAN_TOOLS = Set.of("queryStatus", "searchLogs", "searchKnowledge");

    @Async("diagnoseExecutor")
    public void execute(String taskId,String userId,String tenantId, String question, String serviceName){
        AgentTask task = agentTaskService.getById(taskId);
        if (null == task){
            return;
        }
        // ---- Planner 分支：复杂问题走"计划→执行→汇总"；简单问题走下方原有四步（零改动） ----
        if (plannerEnabled && isComplex(question)){

        }


        Instant deadline = Instant.now().plusSeconds(maxDurationSeconds);   // 任务级超时预算

        int stepNo = 0;
        try {
            task.markRunning();
            agentTaskService.updateById(task);
            // ---- 步骤 3：检索知识（租户隔离） ----
            stepNo++;
            AgentTaskStep retrieval = beginStep(taskId, stepNo, "RETRIEVAL", "knowledgeSearch");
            List<KnowledgeHit> hits;
            try {
                retrieval.recordInput("query=" + truncate(question, 200) + " tenant=" + tenantId);
                hits = knowledgeService.search(tenantId,question);
                retrieval.succeed("命中 " + hits.size() + " 条知识");
            }catch (Exception e){

                retrieval.fail("RETRIEVAL_ERROR", e.getMessage());
                throw  e;
            }finally {
                agentTaskStepService.saveOrUpdate(retrieval);
            }

            checkDeadline(deadline);
            // ---- 步骤 4：受限日志工具（只有明确 serviceName 才查） ----
            stepNo++;
            AgentTaskStep toolStep = beginStep(taskId, stepNo, "TOOL", "searchLogs");
            EvidenceBundle bundle = new EvidenceBundle();
            for (KnowledgeHit hit : hits) {
                bundle.add(hit.evidenceId(), "知识[" + hit.title() + "] " + hit.snippet());
            }
            try {
                toolStep.recordInput("service=" + serviceName + " keyword=(question)");
                if (StringUtils.hasText(serviceName)){
                    // 异步线程没有 UserContext，必须在执行时重新解析用户权限，
                    // 绝不能用"想查什么就放行什么"的重建上下文（否则绕过授权）

                    AppUser appUser = appUserService.getOptById(userId).orElseThrow(() -> new IllegalStateException("执行时用户不存在：" + userId));

                    UserInfo user = new UserInfo();
                    user.setUserId(appUser.getId());
                    user.setTenantId(appUser.getTenantId());


                    List<SysRole> sysRoleList = sysRoleMapper.selectRoleCodesByUserId(appUser.getId());
                    Set<String> roleCodeSet = new HashSet<>();
                    List<String> allowedServices = new ArrayList<>();
                    for (SysRole sysRole : sysRoleList) {
                        roleCodeSet.add(sysRole.getCode());
                        if (StringUtils.hasText(sysRole.getAllowedServices())) {
                            String[] split = sysRole.getAllowedServices().split(",");
                            allowedServices.addAll(List.of(split));
                        }

                    }
                    user.setAllowedServices(allowedServices);
                    user.setRoles(roleCodeSet);

                    String logs = logQueryService.searchLogs(user, serviceName, "", "24h");
                    int idx = 0;
                    for (String line : logs.split("\n")) {
                        if (line.isBlank()){
                            continue;
                        }
                        bundle.add("log-" + (++idx), "日志 " + line);
                    }
                    toolStep.succeed("日志证据 " + bundle.countByPrefix("log-") + " 条");


                }else {
                    toolStep.skip("未指定服务，跳过日志查询");
                }
            }catch (Exception e){
                toolStep.fail("TOOL_ERROR", e.getMessage());
                throw e;
            }finally {
                agentTaskStepService.saveOrUpdate(toolStep);
            }
            checkDeadline(deadline);
            // ---- 步骤 6：模型生成结构化报告（有限重试 + 指数退避） ----

            stepNo++;
            AgentTaskStep modelStep = beginStep(taskId, stepNo, "MODEL", "diagnoseAssistant");
            DiagnoseReport report = null;
            RuntimeException lastError = null;
            try {
                modelStep.recordInput("evidence=" + bundle.size() + " 条，问题=" + truncate(question, 200));

                for (int attempt = 1; attempt <= retryMaxAttempts ; attempt++) {

                    try {
                        report = diagnoseAssistant.diagnose(bundle.toContext(question));
                        break;
                    }catch (RuntimeException e){
                        lastError = e;
                        log.error("模型调用失败 attempt={}/{} taskId={} err={}",
                                attempt, retryMaxAttempts, taskId, e.getMessage());
                        task.increaseRetry();
                        agentTaskService.saveOrUpdate(task);
                        if (attempt < retryMaxAttempts){
                            sleep(backoff(attempt));
                        }

                        checkDeadline(deadline);


                    }

                }
                if (report == null) throw lastError;
                modelStep.succeed("重试 " + task.getRetryCount() + " 次后成功");

            }catch (Exception e){
                modelStep.fail("MODEL_ERROR", e.getMessage());
                throw e;
            }finally {
                agentTaskStepService.saveOrUpdate(modelStep);
            }
            // ---- 步骤 7：后端校验 Schema 与 evidence 引用 ----
            stepNo++;
            AgentTaskStep validateStep = beginStep(taskId, stepNo, "VALIDATION", "reportValidator");

            DiagnoseReport validated;
            try {
                validated = reportValidator.validate(report,bundle);
                validateStep.succeed("引用有效，limitations=" + validated.limitations().size());

            }catch (RuntimeException e){
                validateStep.fail("SCHEMA_INVALID", e.getMessage());
                throw e;
            }finally {
                agentTaskStepService.saveOrUpdate(validateStep);

            }

            // ---- 步骤 8：保存结果，SUCCEEDED ----
            for (DiagnoseReport.PossibleCause possibleCause : validated.possibleCauses()) {
                 if ("high".equals( possibleCause.confidence())){
                     task.holdForApproval(JSON.writeValueAsString(validated));
                     agentTaskService.saveOrUpdate(task);
                     approvalService.registerPending(taskId,userId);
                     log.info("报告含高风险动作，任务挂起等待审批 taskId={}", taskId);
                     return;
                 }
            }
         /*
            if (highRiskActionDetector.containsHighRiskAction(validated)){
                task.holdForApproval(JSON.writeValueAsString(validated));
                agentTaskService.saveOrUpdate(task);
                approvalService.registerPending(taskId,userId);
                log.info("报告含高风险动作，任务挂起等待审批 taskId={}", taskId);
                return;
            }*/
            task.markSucceeded(JSON.writeValueAsString(validated));
            agentTaskService.saveOrUpdate(task);
            log.info("诊断完成 taskId={} causes={}", taskId, validated.possibleCauses().size());

        }catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            failTask(taskId, TaskStatus.TIMED_OUT, "TASK_INTERRUPTED");
        } catch (Exception e) {
            // 任何一步失败：任务 FAILED（状态与错误码已由对应 step 记录）
            failTask(taskId, TaskStatus.FAILED, "DIAGNOSE_FAILED");
            log.error("诊断失败 taskId={}", taskId, e);
        }


    }


    /** 复杂问题：Planner 计划 → 逐子计划执行（失败 SKIP 降级）→ Summary 汇总 → 校验收尾 */
    private void executePlanned(AgentTask task, String userId, String tenantId,
                                String question, Instant deadline) throws Exception{
        String taskId = task.getId();
        int stepNo = 0;
        stepNo++;
        AgentTaskStep planStep = beginStep(taskId, stepNo, "PLANNING", "plannerAssistant");
        List<DiagnosePlan> plans;
        try {
            planStep.recordInput("question=" + question);
            plans = parsePlans(plannerAssistant.plan(question));
            planStep.succeed("计划 " + plans.size() + " 步");

        }catch (Exception e){
            planStep.fail("PLANNING_ERROR", e.getMessage());
            throw e;
        }finally {
            agentTaskStepService.saveOrUpdate(planStep);
        }

        checkDeadline(deadline);
        // 执行时重解析用户权限（异步线程无 UserContext，与现有 TOOL 步同款做法）
        AppUser appUser = appUserService.getOptById(userId)
                .orElseThrow(() -> new IllegalStateException("执行时用户不存在：" + userId));
        UserInfo user = new UserInfo();
        user.setUserId(appUser.getId());
        user.setTenantId(appUser.getTenantId());
        Set<String> roleCodes = new HashSet<>();
        List<String> allowedServices = new ArrayList<>();
        for (SysRole role : sysRoleMapper.selectRoleCodesByUserId(appUser.getId())) {
            roleCodes.add(role.getCode());
            if (StringUtils.hasText(role.getAllowedServices())) {
                allowedServices.addAll(List.of(role.getAllowedServices().split(",")));
            }
        }
        user.setRoles(roleCodes);
        user.setAllowedServices(allowedServices);

        EvidenceBundle bundle = new EvidenceBundle();
        StringBuilder subResults = new StringBuilder();

        // ---- 逐子计划执行（失败策略 SKIP：标注后继续，不终止任务） ----
        for (DiagnosePlan plan : plans) {
            stepNo++;
            AgentTaskStep sub = beginStep(taskId, stepNo, "TOOL", plan.tool());
            try {
                sub.recordInput("goal=" + plan.goal() + " args=" + plan.args());
                String invalid = validatePlan(plan, user);
                if (invalid != null) {                       // 幻觉工具名 / 白名单外服务，不进执行
                    sub.skip("INVALID_PLAN: " + invalid);
                    subResults.append("[步骤").append(plan.step()).append(" SKIPPED] ").append(invalid).append('\n');
                    continue;
                }
                String result = runTool(plan, user, tenantId);
                bundle.add("plan-" + plan.step(), "计划步骤" + plan.step() + " " + result);
                subResults.append("[步骤").append(plan.step()).append("] ")
                        .append(truncate(result, 500)).append('\n');
                sub.succeed(truncate(result, 200));
            } catch (Exception e) {
                sub.fail("SUBPLAN_ERROR", e.getMessage());
                subResults.append("[步骤").append(plan.step()).append(" FAILED] ")
                        .append(e.getMessage()).append('\n');
            } finally {
                agentTaskStepService.saveOrUpdate(sub);
            }
            checkDeadline(deadline);
        }

        // ---- SUMMARY：汇总报告（复用 MODEL 步的有限重试 + 指数退避） ----
        stepNo++;
        AgentTaskStep sumStep = beginStep(taskId, stepNo, "SUMMARY", "summaryAssistant");
        DiagnoseReport report = null;
        RuntimeException lastError = null;
        try {
            sumStep.recordInput("subResults=" + plans.size() + " 条，问题=" + truncate(question, 200));
            for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
                try {
                    report = summaryAssistant.summarize(question, subResults.toString());
                    break;
                } catch (RuntimeException e) {
                    lastError = e;
                    log.error("汇总调用失败 attempt={}/{} taskId={} err={}",
                            attempt, retryMaxAttempts, taskId, e.getMessage());
                    task.increaseRetry();
                    agentTaskService.saveOrUpdate(task);
                    if (attempt < retryMaxAttempts) sleep(backoff(attempt));
                    checkDeadline(deadline);
                }
            }
            if (report == null) throw lastError;
            sumStep.succeed("重试 " + task.getRetryCount() + " 次后成功");
        } catch (Exception e) {
            sumStep.fail("SUMMARY_ERROR", e.getMessage());
            throw e;
        } finally {
            agentTaskStepService.saveOrUpdate(sumStep);
        }

        // ---- 校验 + 收尾（与原四步流水线完全同款） ----
        DiagnoseReport validated = reportValidator.validate(report, bundle);
        for (DiagnoseReport.PossibleCause cause : validated.possibleCauses()) {
            if ("high".equals(cause.confidence())) {
                task.holdForApproval(JSON.writeValueAsString(validated));
                agentTaskService.saveOrUpdate(task);
                approvalService.registerPending(taskId, userId);
                log.info("报告含高风险动作，任务挂起等待审批 taskId={}", taskId);
                return;
            }
        }
        task.markSucceeded(JSON.writeValueAsString(validated));
        agentTaskService.saveOrUpdate(task);
        log.info("Planner 诊断完成 taskId={} causes={}", taskId, validated.possibleCauses().size());

    }

    private AgentTaskStep beginStep(String taskId, int stepNo, String type, String toolName) {
        AgentTaskStep agentTaskStep = new AgentTaskStep();
        agentTaskStep.setTaskId(taskId);
        agentTaskStep.setStepNo(stepNo);
        agentTaskStep.setStepType(type);
        agentTaskStep.setToolName(toolName);
        agentTaskStep.setId(KeyUtils.get32UUID());
        agentTaskStep.setStatus(TaskStatus.RUNNING.name());
        agentTaskStepService.save(agentTaskStep);
        return agentTaskStep;
    }

    /** 超过任务最大耗时 → TIMED_OUT（设计文档可靠性第 5 条） */
    private void checkDeadline(Instant deadline) throws InterruptedException {
        if (Instant.now().isAfter(deadline)) throw new InterruptedException("任务超时");
    }

    private long backoff(int attempt) { return retryBackoffMs * (1L << (attempt - 1)); }  // 1s,2s,4s

    private void sleep(long ms) throws InterruptedException { Thread.sleep(ms); }

    private void failTask(String taskId, TaskStatus status, String errorCode) {

        agentTaskService.getOptById(taskId).ifPresent(t -> {
            t.markFailed(status, errorCode);
            agentTaskService.saveOrUpdate(t);
        });
    }

    private String truncate(String s, int max) { return s.substring(0, Math.min(s.length(), max)); }


    private boolean isComplex(String question){
        return question.contains("并且") || question.contains("同时") || question.contains("然后")
                || question.contains("顺便") || question.length() > 100;
    }

    /** 计划校验：返回 null=合法；否则返回不可信原因（Planner 输出也是不可信输入） */
    private String validatePlan(DiagnosePlan plan,UserInfo userInfo){
        if (!PLAN_TOOLS.contains(plan.tool())){
            return "未知工具 " + plan.tool();
        }

        String serviceName = plan.args().get("serviceName");
        if (!StringUtils.hasText(serviceName) && !userInfo.getAllowedServices().contains(serviceName)){
            return "服务不在授权白名单 " + serviceName;
        }

        return null;
    }

    /** 三个已注册工具的受限分发（只转发校验过的 tool/args，无兜底 catch） */
    private String runTool(DiagnosePlan plan,UserInfo userInfo,String tenantId) throws Exception{
        return switch (plan.tool()){
            case "queryStatus" -> {
                ServiceStatus st = opsRepository.getServiceStatus(plan.args().get("serviceName"));
                yield st.service() + " " + st.status() + " " + st.detail();

            }
            case "searchLogs" -> logQueryService.searchLogs(userInfo, plan.args().get("serviceName"), "", "24h");
            case "searchKnowledge" ->{
                List<KnowledgeHit> hits = knowledgeService.search(tenantId, plan.args().get("query"));
                yield hits.stream().map( h -> "知识[" + h.title() + "] " + h.snippet())
                        .collect(Collectors.joining("\n"));
            }
            default -> throw new IllegalArgumentException("未知工具 " + plan.tool());

        };
    }

    /** Planner 输出 JSON → List<DiagnosePlan>（LLM 常裹 ```json 围栏，先剥离再反序列化） */
    private List<DiagnosePlan> parsePlans(String planJson) throws Exception{
        String json = planJson.trim();
        if (json.startsWith("```")) {
            json = json.substring(json.indexOf('\n') + 1, json.lastIndexOf("```")).trim();
        }
        return JSON.readValue(json, new TypeReference<List<DiagnosePlan>>() {});
    }
}
