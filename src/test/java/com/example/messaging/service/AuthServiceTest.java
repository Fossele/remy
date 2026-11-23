package com.example.messaging.service;

import com.example.messaging.dto.*;
import com.example.messaging.entity.RefreshToken;
import com.example.messaging.entity.User;
import com.example.messaging.repository.RefreshTokenRepository;
import com.example.messaging.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenTtl", Duration.ofDays(30));
    }

    @Test
    void loginAuthenticatesUserAndCreatesRefreshToken() {
        User user = user("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        verify(authenticationManager).authenticate(any());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken token = RefreshToken.builder()
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("refresh-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token is invalid or expired");

        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutRevokesStoredRefreshToken() {
        RefreshToken token = RefreshToken.builder().revoked(false).build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        authService.logout(new RefreshTokenRequest("refresh-token"));

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void loginRejectsDeletedUser() {
        User deletedUser = user("alice");
        deletedUser.setDeleted(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void refreshCreatesAccessTokenForValidStoredToken() {
        UUID userId = UUID.randomUUID();
        User user = user("alice");
        user.setId(userId);
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.createAccessToken(user)).thenReturn("access-token");
        when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);

        AuthResponse response = authService.refresh(new RefreshTokenRequest("refresh-token"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().username()).isEqualTo("alice");
    }

    @Test
    void refreshRejectsUnavailableUser() {
        UUID userId = UUID.randomUUID();
        RefreshToken token = RefreshToken.builder()
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("refresh-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User is unavailable");
        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutIgnoresUnknownRefreshToken() {
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.empty());

        authService.logout(new RefreshTokenRequest("unknown"));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    private User user(String username) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hashed-password")
                .build();
    }
}
