package com.luzihao.core.user;

import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class UserInfo {

    private String userId;
    private String tenantId;
    private Set<String> roles;
    private List<String> allowedServices;

    public boolean hasRole(String code) {
        return roles != null && roles.contains(code);
    }

}
