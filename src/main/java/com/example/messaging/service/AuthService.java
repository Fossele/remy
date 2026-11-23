package com.example.messaging.service;

import com.example.messaging.dto.*;
import com.example.messaging.entity.RefreshToken;
import com.example.messaging.entity.User;
import com.example.messaging.repository.RefreshTokenRepository;
import com.example.messaging.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.security.jwt.refresh-token-ttl:30d}")
    private Duration refreshTokenTtl;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserResponse userResponse = userService.register(request);
        User user = userRepository.findById(userResponse.id()).orElseThrow();
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.username(), request.password()
                )
        );
        User user = userRepository.findByUsername(request.username())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow();
        return createAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(hash(request.refreshToken()))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));

        User user = userRepository.findById(storedToken.getUserId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("User is unavailable"));

        return new AuthResponse(
                jwtService.createAccessToken(user),
                request.refreshToken(),
                jwtService.accessTokenExpiresInSeconds(),
                UserResponse.fromEntity(user)
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hash(request.refreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse createAuthResponse(User user) {
        String refreshTokenValue = generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(refreshTokenValue))
                .expiresAt(Instant.now().plus(refreshTokenTtl))
                .revoked(false)
                .build());

        return new AuthResponse(
                jwtService.createAccessToken(user),
                refreshTokenValue,
                jwtService.accessTokenExpiresInSeconds(),
                UserResponse.fromEntity(user)
        );
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
