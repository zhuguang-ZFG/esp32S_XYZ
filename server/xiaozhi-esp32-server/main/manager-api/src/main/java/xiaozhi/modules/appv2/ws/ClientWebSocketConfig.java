package xiaozhi.modules.appv2.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import lombok.RequiredArgsConstructor;

/**
 * Edge-A WSS：{@code /ws/v1/client}（部署含 {@code context-path} 时为 {@code /xiaozhi/ws/v1/client}）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ClientWebSocketConfig implements WebSocketConfigurer {

    private final ClientEdgeWebSocketHandler clientEdgeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clientEdgeWebSocketHandler, "/ws/v1/client")
                .setAllowedOriginPatterns("*");
    }
}
