package com.game.bunker.service;

import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.entity.User;
import com.game.bunker.entity.UserStatus;
import com.game.bunker.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@NoArgsConstructor
@AllArgsConstructor
public class UserService {
    private UserRepository userRepository;

    public User saveUser(User user){
        return userRepository.save(user);
    }

    

}
