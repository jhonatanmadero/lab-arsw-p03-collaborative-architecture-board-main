package edu.eci.arsw.collabboard.infrastructure.web.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP/WebSocket configuration, kept separate from REST controllers.
 *
 * <ul>
 *   <li>Endpoint: {@code /ws}</li>
 *   <li>Application prefix: {@code /app} (handled by {@code @MessageMapping})</li>
 *   <li>Simple in-memory broker: {@code /topic} (Board broadcast) and {@code /queue} (private rejections)</li>
 *   <li>User prefix: {@code /user} (session-scoped destinations)</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
