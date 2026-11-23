package com.example.messaging.service;

import com.example.messaging.dto.RegisterRequest;
import com.example.messaging.dto.UserResponse;
import com.example.messaging.entity.User;
import com.example.messaging.entity.UserRole;
import com.example.messaging.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerHashesPasswordAndCreatesUser() {
        UUID userId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123", "Alice");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        UserResponse response = userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("alice", "alice@example.com", "password123", null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username is already in use");

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(
                new RegisterRequest("alice", "alice@example.com", "password123", null)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email is already in use");

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }
}
