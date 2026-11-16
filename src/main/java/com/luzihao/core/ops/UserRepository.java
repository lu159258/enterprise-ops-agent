package com.luzihao.core.ops;

import com.luzihao.core.user.UserInfo;

public interface UserRepository {
    UserInfo findById(String userId);

}
