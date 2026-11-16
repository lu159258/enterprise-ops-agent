package com.luzihao.core.user;

public final class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    private UserContext(){}

    public static void set(UserInfo user){
        HOLDER.set(user);
    }

    public static UserInfo get() {
        return HOLDER.get();
    }

    public static void clear(){
        HOLDER.remove();
    }

    public static UserInfo require(){
        UserInfo userInfo = HOLDER.get();
        if (null == userInfo){
            throw new IllegalStateException("未识别的用户，拒接执行");
        }

        return userInfo;
    }
}
