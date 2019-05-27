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

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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

    enum RequestAcceptance {
        ACCEPT(HttpStatus.OK, Map.of("accept", Boolean.TRUE)),
        REJECT(HttpStatus.OK, Map.of("accept", Boolean.FALSE)),
        FORBIDDEN(HttpStatus.FORBIDDEN, Map.of()),
        ;

        final HttpStatus status;
        final Map<String, Boolean> body;

        RequestAcceptance(HttpStatus status, Map<String, Boolean> body) {
            this.status = status;
            this.body = body;
        }
    }

    @Bean(name = "rateLimiterAdapter")
    Function<String, UnaryOperator<Mono<RequestAcceptance>>> rateLimiterAdapter(RateLimiterRegistry rateLimiterRegistry) {
        return apiKey -> {
            RateLimiterOperator<RequestAcceptance> operator =
                    RateLimiterOperator.of(rateLimiterRegistry.rateLimiter(apiKey));
            return mono -> Mono.from(operator.apply(mono))
                    .onErrorResume(RequestNotPermitted.class, requestNotPermitted -> Mono.just(RequestAcceptance.REJECT));
        };
    } 

    @Bean(name = "serverIdFilter")
    Function<ServerRequest.Headers, UnaryOperator<Mono<RequestAcceptance>>> serverIdFilter() {
        return headers -> {
            List<String> appIds = headers.header("X-APP-ID");
            return mono -> 
                    mono.filter(response -> !appIds.isEmpty())
                            .switchIfEmpty(Mono.just(RequestAcceptance.FORBIDDEN));
        };
    } 

    @Bean
    RouterFunction<ServerResponse> routerFunction(
            @Qualifier("rateLimiterAdapter") Function<String, UnaryOperator<Mono<RequestAcceptance>>> rateLimiterAdapter,
            @Qualifier("serverIdFilter") Function<ServerRequest.Headers, UnaryOperator<Mono<RequestAcceptance>>> serverIdFilter) {
        return route()
                .POST("/rate", request -> {
                    ServerRequest.Headers headers = request.headers();
                    List<String> apiKeys = headers.header("X-USER-API-KEY");
                    String apiKey = apiKeys.isEmpty() ? "anonymous" : apiKeys.get(0);
                    UnaryOperator<Mono<RequestAcceptance>> adapter = rateLimiterAdapter.apply(apiKey);
                    return Mono.just(RequestAcceptance.ACCEPT)
                            .compose(adapter)
                            .compose(serverIdFilter.apply(headers))
                            .flatMap(map -> ServerResponse.status(map.status)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("X-USER-API-KEY", apiKey)
                                    .headers(hs -> hs.addAll("X-APP-ID", headers.header("X-APP-ID")))
                                    .body(Mono.just(map.body), new ParameterizedTypeReference<Map<String, Boolean>>() {}))
                            .doOnSuccess(response -> logger.info(
                                    "response: {}, server: {}, api-key: {}",
                                    response.statusCode(),
                                    headers.header("X-APP-ID"),
                                    apiKey));
                }).build();
    }
}
