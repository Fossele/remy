package com.example.messaging.service;

import com.example.messaging.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "accessTokenTtl", Duration.ofMinutes(15));
    }

    @Test
    void createsTokenWithUserIdentityAndRoleClaims() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed-password")
                .build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(Jwt.withTokenValue("encoded-token")
                        .header("alg", "HS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plus(Duration.ofMinutes(15)))
                        .claim("sub", userId.toString())
                        .claim("username", "alice")
                        .claim("roles", java.util.List.of("USER"))
                        .build());

        String token = jwtService.createAccessToken(user);

        assertThat(token).isEqualTo("encoded-token");
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getClaimAsString("username")).isEqualTo("alice");
        assertThat(claims.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(claims.getExpiresAt()).isAfter(claims.getIssuedAt());
    }
}
