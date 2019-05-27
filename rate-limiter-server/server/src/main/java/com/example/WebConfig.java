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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@EnableWebFlux
public class WebConfig implements WebFluxConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    private final MonoLimiterAdapterRegistry monoLimiterAdapterRegistry;
    private final AppController appController;
    private final ObjectMapper objectMapper;

    public WebConfig(
            MonoLimiterAdapterRegistry monoLimiterAdapterRegistry,
            AppController appController,
            ObjectMapper objectMapper) {
        this.monoLimiterAdapterRegistry = monoLimiterAdapterRegistry;
        this.appController = appController;
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(0)
    WebFilter apiRateLimiterFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String key = ApiPathAndApiKey.from(request)
                    .map(ApiPathAndApiKey::rateLimiterKey)
                    .orElse("anonymous");
            UnaryOperator<Mono<ServerWebExchange>> adapter = monoLimiterAdapterRegistry.getAdapter(key);
            return adapter.apply(Mono.just(exchange))
                    .doOnEach(sig -> logForFilter(request))
                    .flatMap(chain::filter);
        };
    }

    private static void logForFilter(ServerHttpRequest request) {
        logger.info(
                "api rate limiter filter - framework-id: {}, request-id: {} , path: {}, x-api-key: {}",
                request.getId(),
                request.getHeaders().get("X-REQUEST-ID"),
                request.getPath(),
                request.getHeaders().get("X-API-KEY"));
    }

    @Bean
    @Order(-1)
    WebExceptionHandler noApiKeyHandler() {
        return (exchange, ex) -> Mono.<Void>error(ex)
                .doOnError(
                        NoApiKey.class,
                        e -> logger.info("api rate limiter filter - no x-api-key: {}, {}", e.getClass().getSimpleName(), e.getMessage()))
                .onErrorResume(NoApiKey.class, noApiKey -> writeErrorResponse(noApiKey, exchange.getResponse(), exchange.getRequest()));
    }

    private Mono<Void> writeErrorResponse(NoApiKey noApiKey, ServerHttpResponse response, ServerHttpRequest request) {
        DataBuffer buffer = response.bufferFactory().allocateBuffer();
        Map<String, String> obj = Map.of("path", request.getPath().value(), "message", noApiKey.getMessage());
        Mono<DataBuffer> mono = Mono.fromCallable(() -> buffer.write(writeJson(obj), StandardCharsets.UTF_8));
        return response.writeWith(mono);
    }

    private String writeJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("error on writing json", e);
        }
    }

    @Bean
    @Order(-2)
    WebExceptionHandler requestNotPermitted() {
        return (exchange, ex) -> Mono.<Void>error(ex)
                .doOnError(
                        RequestNotPermitted.class,
                        e -> logger.info("api rate reached limit, path: {}, api: {}",
                                exchange.getRequest().getPath(),
                                exchange.getRequest().getHeaders().get("X-API-KEY")))
                .onErrorResume(
                        RequestNotPermitted.class,
                        requestNotPermitted -> writeRequestNotPermitted(exchange.getResponse(), exchange.getRequest()));
    }

    private Mono<Void> writeRequestNotPermitted(ServerHttpResponse response, ServerHttpRequest request) {
        DataBuffer buffer = response.bufferFactory().allocateBuffer();
        Map<String, String> obj = Map.of("path", request.getPath().value(), "message", "reached at rate limit.");
        Mono<DataBuffer> data = Mono.fromCallable(() -> buffer.write(writeJson(obj), StandardCharsets.UTF_8));
        if (response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS)) {
            return response.writeWith(data);
        }
        return Mono.empty();
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction() {
        return route()
                .GET("/api", accept(MediaType.APPLICATION_JSON), appController::get)
                .build();
    }
}

