package com.luzihao.core.diagnose;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luzihao.core.ai.DiagnoseAssistant;
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

    @Async("diagnoseExecutor")
    public void execute(String taskId,String userId,String tenantId, String question, String serviceName){
        AgentTask task = agentTaskService.getById(taskId);
        if (null == task){
            return;
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

}
