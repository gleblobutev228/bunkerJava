package com.game.bunker.service;

import com.game.bunker.dto.request.UserCreationRequestDto;
import com.game.bunker.entity.User;
import com.game.bunker.entity.UserStatus;
import com.game.bunker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;

    public User saveUser(User user){
        return userRepository.save(user);
    }

    public Optional<User> getUserByNickname(String username) {
        return userRepository.findByUsername(username);
    }

    public User createUser(UserCreationRequestDto dto) {
        User user = new User();
        user.setUserName(dto.getUserName());
        user.setStatus(UserStatus.NOT_READY);
        user.setCreationTime(LocalDateTime.now());

        return user;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //Ищем пользователя в базе через репозиторий
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));

        // 2. Возвращаем объект UserDetails
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                //заглушка
                .password("")
                // Здесь можно указать роли, если они есть. Если нет — ставим пустой список/USER
                .authorities(new ArrayList <>())
                .build();
    }

}
