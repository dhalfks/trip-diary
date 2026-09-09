package com.tripdiary.user;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tripdiary.global.error.BusinessException;
import com.tripdiary.global.error.ErrorCode;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) { this.users = users; }

    public UserResponse me(UUID id) { return UserResponse.from(find(id)); }

    @Transactional
    public UserResponse update(UUID id, String nickname) {
        User user = find(id);
        user.changeNickname(nickname.strip());
        users.flush();
        return UserResponse.from(user);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }
}
