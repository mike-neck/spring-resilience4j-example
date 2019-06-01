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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ServerApp {

    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);

    public static void main(String[] args) {
        SpringApplication.run(ServerApp.class, args);
    }

    private final ParameterizedTypeReference<Map<String, String>> JSON_TYPE = new ParameterizedTypeReference<>() {
    };

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE).setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    Jackson2JsonEncoder jsonEncoder(ObjectMapper objectMapper) {
        return new Jackson2JsonEncoder(objectMapper, MimeTypeUtils.APPLICATION_JSON);
    }

    @Bean
    WebClient webClient() {
        return WebClient.create("http://localhost:8000");
    }

    @Bean("objectFactory")
    Function<String, Map<String, String>> objectFactory(@Value("${app.id}") String appId) {
        return userApiKey -> Map.of(
                "application_id", appId,
                "api_key", userApiKey,
                "time", DateTimeFormatter.ISO_INSTANT.format(Instant.now(Clock.systemUTC())));
    }

    @Bean
    RouterFunction<ServerResponse> routerFunction(@Qualifier("objectFactory") Function<String, Map<String, String>> objectFactory) {
        return route()
                .GET("/api", request -> {
                    List<String> userApiKey = request.headers().header("X-USER-API-KEY");
                    if (userApiKey.isEmpty()) {
                        return ServerResponse.status(HttpStatus.FORBIDDEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Mono.just(Map.of("message", "request missing api key")), JSON_TYPE);
                    } else {
                        String apiKey = userApiKey.get(0);
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-USER-API-KEY", apiKey)
                                .body(Mono.just(objectFactory.apply(apiKey)), JSON_TYPE);
                    }
                }).build();
    }
}
