package com.luzihao.core.diagnose;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApprovalService {

    private final Map<String,String> pendingByTask = new ConcurrentHashMap<>();

    public void registerPending(String taskId,String requesterId){
        pendingByTask.put(taskId,requesterId);
    }

    public boolean isPending(String taskId){
        return pendingByTask.containsKey(taskId);
    }

    public String clear(String taskId){
        return pendingByTask.remove(taskId);
    }
}
