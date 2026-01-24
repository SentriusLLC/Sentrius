package io.sentrius.sso.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class NettyServerConfig {

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer() {
        return factory -> factory.addServerCustomizers(httpServer ->
            httpServer
                .idleTimeout(Duration.ofMinutes(60)) // 60 minute idle timeout
                .option(ChannelOption.SO_KEEPALIVE, true)
                .doOnConnection(conn -> {
                    conn.addHandlerLast("idleStateHandler",
                        new IdleStateHandler(0, 0, 65, TimeUnit.MINUTES));
                })
        );
    }
}