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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class RateLimiterServiceApp {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterServiceApp.class);

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterServiceApp.class, args);
    }

    @Bean
    RateLimiterConfig rateLimiterConfig() {
        return RateLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(100L))
                .limitRefreshPeriod(Duration.ofSeconds(30))
                .limitForPeriod(5)
                .build();
    }

    @Bean
    RateLimiterRegistry rateLimiterRegistry(RateLimiterConfig rateLimiterConfig) {
        return RateLimiterRegistry.of(rateLimiterConfig);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    Jackson2JsonEncoder jsonEncoder(ObjectMapper objectMapper) {
        return new Jackson2JsonEncoder(objectMapper, MimeTypeUtils.APPLICATION_JSON);
    }

    @Bean
    RateLimiterAdapterManager rateLimiterAdapter(RateLimiterRegistry rateLimiterRegistry) {
        return apiKey -> {
            RateLimiterOperator<RequestAcceptance> operator =
                    RateLimiterOperator.of(rateLimiterRegistry.rateLimiter(apiKey));
            return mono -> Mono.from(operator.apply(mono))
                    .onErrorResume(
                            RequestNotPermitted.class,
                            requestNotPermitted -> Mono.just(RequestAcceptance.REJECT));
        };
    }

    interface RateLimiterAdapterManager {
        RateLimiterAdapter take(String name);
    }

    interface RateLimiterAdapter {
        Mono<RequestAcceptance> apply(Mono<RequestAcceptance> mono);
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction(
            RateLimiterAdapterManager rateLimiterAdapter) {
        return route()
                .POST("/rate", request -> handle(rateLimiterAdapter, request)).build();
    }

    private Mono<ServerResponse> handle(RateLimiterAdapterManager rateLimiterAdapter, ServerRequest request) {
        ServerRequest.Headers headers = request.headers();
        List<String> apiKeys = headers.header("X-USER-API-KEY");
        String apiKey = apiKeys.isEmpty() ? "anonymous" : apiKeys.get(0);
        RateLimiterAdapter adapter = rateLimiterAdapter.take(apiKey);
        return Mono.just(RequestAcceptance.ACCEPT)
                .compose(adapter::apply)
                .doOnSuccess(acceptance -> logger.info(
                        "response: {}. body: {}, server: {}, api-key: {}, ", 
                        acceptance.status,
                        acceptance.body,
                        headers.header("X-APP-ID"),
                        apiKey))
                .flatMap(map -> ServerResponse.status(map.status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-USER-API-KEY", apiKey)
                        .headers(hs -> hs.addAll("X-APP-ID", headers.header("X-APP-ID")))
                        .body(Mono.just(map.body), new ParameterizedTypeReference<Map<String, Boolean>>() {
                        }));
    }
}
