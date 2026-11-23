package com.example.messaging.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    @Test
    void connectAttachesAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("roles", List.of("USER"))
                .build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);

        Message<?> result = interceptor.preSend(connectMessage("Bearer token"), channel);
        Principal principal = StompHeaderAccessor.wrap(result).getUser();

        assertThat(principal).isNotNull();
        assertThat(principal.getName()).isEqualTo(userId.toString());
    }

    @Test
    void connectRejectsMissingBearerToken() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bearer token is required");
    }

    private Message<?> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
