package com.luzihao.api.controller;

import com.luzihao.api.dto.DiagnoseRequest;
import com.luzihao.api.dto.DiagnoseStartResponse;
import com.luzihao.core.diagnose.DiagnoseService;
import com.luzihao.core.user.UserContext;
import com.luzihao.core.user.UserInfo;
import com.luzihao.infra.entity.AgentTask;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class DiagnoseController {
    @Autowired
    private DiagnoseService diagnoseService;

    /** Idempotency-Key：客户端生成的请求唯一标识（如 UUID），重试时带上同一个值 */
    @PostMapping("/diagnose")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DiagnoseStartResponse diagonse(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                          @Valid @RequestBody DiagnoseRequest request){
        UserInfo userInfo = UserContext.require();
        AgentTask start = diagnoseService.start(userInfo, request.question(), request.serviceName(), idempotencyKey);
        return new DiagnoseStartResponse(start.getId(),start.getStatus(),start.getIdempotencyKey() != null);
    }

}
