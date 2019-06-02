/*
 * Copyright 2019 Shinya Mochida
 *
 * Licensed under the Apache License,Version2.0(the"License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,software
 * Distributed under the License is distributed on an"AS IS"BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ApiServerApp {

    private static final Logger logger = LoggerFactory.getLogger(ApiServerApp.class);

    public static void main(String[] args) {
        SpringApplication.run(ApiServerApp.class, args);
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction() {
        return route()
                .GET("/api", request -> {
                    Instant now = Instant.now();
                    String time = DateTimeFormatter.ISO_INSTANT.format(now);
                    List<String> userApiKey = request.headers().header("X-USER-API-KEY");
                    if (userApiKey.isEmpty()) {
                        logger.info("user-api-key missing, remote: {}, request-id: {}", request.remoteAddress(), request.headers().header("X-APP-REQUEST-ID"));
                        return ServerResponse.status(HttpStatus.FORBIDDEN)
                                .body(Mono.just(Map.of("accept", false)), new ParameterizedTypeReference<>() {
                                });
                    }
                    logger.info(
                            "success, request-id: {}, user-api-key: {}",
                            request.headers().header("X-APP-REQUEST-ID"),
                            userApiKey);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-USER-API-KEY", userApiKey.get(0))
                            .body(Mono.just(Map.of("time", time)), new ParameterizedTypeReference<>() {
                            });
                }).build();
    }

    @Bean
    Jackson2JsonEncoder encoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new Jackson2JsonEncoder(objectMapper, MimeTypeUtils.APPLICATION_JSON);
    }

    @Bean
    WebFilter appIdFilter(Jackson2JsonEncoder encoder) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            List<String> appId = request.getHeaders().get("X-APP-ID");
            if (appId == null || appId.isEmpty()) {
                logger.info("x-app-id missing, address: {}", request.getRemoteAddress());
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.FORBIDDEN);
                Flux<DataBuffer> data = encoder.encode(
                        Mono.just(Map.of("message", "invalid request")),
                        response.bufferFactory(),
                        ResolvableType.forType(new ParameterizedTypeReference<Map<String, String>>() {
                        }), MimeTypeUtils.APPLICATION_JSON, Map.of());
                return response.writeWith(data);
            }
            return chain.filter(exchange);
        };
    }
}
