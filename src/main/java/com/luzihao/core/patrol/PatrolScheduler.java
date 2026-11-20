package com.luzihao.core.patrol;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luzihao.core.diagnose.DiagnoseService;
import com.luzihao.core.notify.Notification;
import com.luzihao.core.notify.NotificationChannel;
import com.luzihao.core.ops.OpsRepository;
import com.luzihao.core.ops.ServiceStatus;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import com.luzihao.infra.entity.AppUser;
import com.luzihao.infra.entity.SysRole;
import com.luzihao.infra.mapper.SysRoleMapper;
import com.luzihao.infra.service.AppUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PatrolScheduler {
    @Autowired
    private OpsRepository opsRepository;
    @Autowired
    private DiagnoseService diagnoseService;
    @Autowired
    private NotificationChannel notificationChannel;
    private volatile UserInfo patrolUser;   // 系统身份，首次巡检时加载一次

    @Autowired
    private AppUserService appUserService;
    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Value("${app.patrol.cron}")
    private String cron;
    @Value("${app.tools.service-whitelist}")
    private List<String> whiteList;

    @Value("${app.patrol.enabled}")
    private boolean enabled;

    @Scheduled(cron = "${app.patrol.cron}")
    public void patrol(){
        if (!enabled){
            return;
        }

        for (String service : whiteList) {
            try {
                ServiceStatus status = opsRepository.getServiceStatus(service);
                if ("DOWN".equals(status.status()) || "UNKNOWN".equals(status.status())){
                    // 幂等键：同周期同服务只建一个任务（Phase 1 的唯一索引机制兜底）
                    String idemKey = "patrol:%s:%s".formatted(service,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
                    String question = "巡检发现服务状态异常(" + status.status() + ")，请诊断原因";
                    AgentTask task = diagnoseService.start(patrolUser(), question, service, idemKey);
                    log.info("巡检异常已建诊断任务 service={} taskId={}", service, task.getId());
                    notificationChannel.send(Notification.taskCreated(task,status));


                }
            }catch (Exception e){
                log.error("巡检单服务失败 service={}，继续巡检下一个", service, e);  // 单服务失败不中断整体

            }
        }
    }


    private UserInfo patrolUser(){
        if(null != patrolUser){
            return patrolUser;
        }
        AppUser user = appUserService.getOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, "PATROL_SYSTEM"));
        if (null == user || !Boolean.TRUE.equals(user.getEnabled())){
            throw new IllegalStateException("PATROL_SYSTEM 系统账号不存在或未启用");

        }


        List<SysRole> sysRoleList = sysRoleMapper.selectRoleCodesByUserId(user.getId());
        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setTenantId(user.getTenantId());
        info.setRoles(sysRoleList.stream().map(SysRole::getCode).collect(Collectors.toSet()));
        info.setAllowedServices(sysRoleList.stream().filter(sysRole ->
            StringUtils.hasText(sysRole.getAllowedServices())
        ).flatMap(sysRole -> Arrays.stream(sysRole.getAllowedServices().split(","))).toList());

        patrolUser = info;
        return info;
    }
}
