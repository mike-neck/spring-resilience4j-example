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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RateLimiterFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterFilter.class);

    private static final ParameterizedTypeReference<Map<String, String>> JSON_TYPE = new ParameterizedTypeReference<>() {
    };

    private final RateLimiterService rateLimiterService;
    private final Jackson2JsonEncoder jsonEncoder;

    public RateLimiterFilter(
            RateLimiterService rateLimiterService,
            Jackson2JsonEncoder jsonEncoder) {
        this.rateLimiterService = rateLimiterService;
        this.jsonEncoder = jsonEncoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Optional<String> userApiKey = extractUserApiKey(exchange);
        String k = userApiKey.orElse("<nil>");
        return userApiKey
                .map(key -> ascRateLimit(
                        key,
                        Mono.defer(() -> chain.filter(exchange)),
                        tooManyRequest(exchange.getResponse(), k)))
                .orElseGet(() -> forbidden(exchange.getResponse(), k))
                .onErrorResume(ex -> internalServerError(exchange.getResponse(), k, ex));
    }

    private Optional<String> extractUserApiKey(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        Optional<List<String>> apiKeys = Optional.ofNullable(
                request.getHeaders().get("X-USER-API-KEY"));
        return apiKeys
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0));
    }

    private Mono<Void> ascRateLimit(String userApiKey, Mono<Void> onSuccess, Mono<Void> onFailure) {
        return rateLimiterService.isAcceptable(userApiKey)
                .flatMap(accept -> accept? onSuccess: onFailure);
    }

    private Mono<Void> forbidden(
            ServerHttpResponse response,
            String apiKey) {
        return Mono.defer(() -> {
            logger.info("forbidden, api-key: {}", apiKey);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            Flux<DataBuffer> json = jsonEncoder.encode(
                    Mono.just(Map.of("message", "api key is required.")),
                    response.bufferFactory(),
                    ResolvableType.forType(JSON_TYPE),
                    MimeTypeUtils.APPLICATION_JSON,
                    Map.of());
            return response.writeWith(json);
        });
    }

    private Mono<Void> tooManyRequest(ServerHttpResponse response, String apiKey) {
        return Mono.defer(() -> {
            logger.info("too many request, api-key: {}", apiKey);
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            Flux<DataBuffer> json = jsonEncoder.encode(
                    Mono.just(Map.of("message", "retry again 30 sec later.")),
                    response.bufferFactory(),
                    ResolvableType.forType(JSON_TYPE),
                    MimeTypeUtils.APPLICATION_JSON,
                    Map.of());
            return response.writeWith(json);
        });
    }

    private Mono<Void> internalServerError(
            ServerHttpResponse response,
            String apiKey,
            Throwable ex) {
        return Mono.defer(() -> {
            logger.info("error, api-key: {}, error: {}, message: {}",
                    apiKey,
                    ex.getClass().getCanonicalName(),
                    ex.getMessage(),
                    ex);
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            Flux<DataBuffer> json = jsonEncoder.encode(
                    Mono.just(Map.of("message", "unknown error happening.")),
                    response.bufferFactory(),
                    ResolvableType.forType(JSON_TYPE),
                    MimeTypeUtils.APPLICATION_JSON,
                    Map.of());
            return response.writeWith(json);
        });
    }
}
