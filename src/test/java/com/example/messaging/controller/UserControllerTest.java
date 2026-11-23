package com.example.messaging.controller;

import com.example.messaging.dto.UserResponse;
import com.example.messaging.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    @Test
    void currentUserUsesJwtSubject() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .build();
        UserResponse response = new UserResponse(
                userId, "alice", "alice@example.com", "Alice", null, "USER"
        );
        when(userService.getById(userId)).thenReturn(response);

        UserResponse result = controller.currentUser(jwt);

        assertThat(result).isEqualTo(response);
        verify(userService).getById(userId);
    }
}
