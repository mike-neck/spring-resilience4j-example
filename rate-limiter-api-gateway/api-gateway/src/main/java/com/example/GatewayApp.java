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
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class GatewayApp {

    private static final Logger logger = LoggerFactory.getLogger(GatewayApp.class);

    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }

    @Bean
    RouteLocator routeLocator(RouteLocatorBuilder builder, @Qualifier("rateLimiter") GatewayFilter rateLimiter) {
        return builder.routes()
                .route("api", spec -> spec.path("/api")
                        .filters(fspec -> fspec.addRequestHeader("X-APP-ID", "Gateway")
                                .filter(loggingFilter())
                                .filter(requestIdFilter())
                                .filter(rateLimiter))
                        .uri("http://localhost:8000/api")
                ).build();
    }

    @Bean("loggingFilter")
    GatewayFilter loggingFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String id = request.getId();
            List<String> apiKey = request.getHeaders().get("X-USER-API-KEY");
            logger.info("request, id: {}, user-api-key: {}", id, apiKey);
            return chain.filter(exchange);
        };
    }

    @Bean("requestIdFilter")
    GatewayFilter requestIdFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String id = request.getId();
            ServerHttpRequest mutated = request.mutate()
                    .header("X-APP-REQUEST-ID", id)
                    .build();
            ServerWebExchange webExchange = exchange.mutate()
                    .request(mutated)
                    .build();
            return chain.filter(webExchange);
        };
    }

    @Bean
    RateLimiterConfig rateLimiterConfig() {
        return RateLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100L))
                .limitRefreshPeriod(Duration.ofSeconds(30L))
                .limitForPeriod(5)
                .build();
    }

    @Bean
    RateLimiterRegistry rateLimiterRegistry(RateLimiterConfig rateLimiterConfig) {
        return RateLimiterRegistry.of(rateLimiterConfig);
    }

    @Bean
    Jackson2JsonEncoder encoder() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new Jackson2JsonEncoder(objectMapper, MimeTypeUtils.APPLICATION_JSON);
    }

    @Bean("rateLimiter")
    GatewayFilter rateLimiterFilter(
            RateLimiterRegistry rateLimiterRegistry,
            Jackson2JsonEncoder encoder) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            List<String> apiKey = request.getHeaders().get("X-USER-API-KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                return forbidden(exchange, encoder);
            }
            String key = apiKey.get(0);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(key);
            return Mono.just(key)
                    .compose(RateLimiterOperator.of(rateLimiter))
                    .then(Mono.defer(() -> chain.filter(exchange)))
                    .onErrorResume(
                            RequestNotPermitted.class,
                            requestNotPermitted -> tooManyRequest(exchange, encoder));
        };
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange, Jackson2JsonEncoder encoder) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        Flux<DataBuffer> data = encoder.encode(
                Mono.just(Map.of("message", "api key is required")),
                response.bufferFactory(),
                ResolvableType.forType(new ParameterizedTypeReference<Map<String, String>>() {
                }),
                MimeTypeUtils.APPLICATION_JSON,
                Map.of());
        return response.writeWith(data);
    }

    private static Mono<Void> tooManyRequest(ServerWebExchange exchange, Jackson2JsonEncoder encoder) {
        logger.info(
                "too many request, request: {}, user-api-key: {}",
                exchange.getRequest().getId(),
                exchange.getRequest().getHeaders().get("X-USER-API-KEY"));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        Flux<DataBuffer> data = encoder.encode(
                Mono.just(Map.of("message", "retry 30 sec later")),
                response.bufferFactory(),
                ResolvableType.forType(new ParameterizedTypeReference<Map<String, String>>() {
                }),
                MimeTypeUtils.APPLICATION_JSON,
                Map.of());
        return response.writeWith(data);
    }
}
