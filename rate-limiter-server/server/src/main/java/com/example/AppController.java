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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
class AppController {

    private static final Logger logger = LoggerFactory.getLogger(AppController.class);

    Mono<ServerResponse> get(ServerRequest request) {
        logger.info("app-controller: path: {}, method: {}, request-id: {}",
                request.path(),
                request.methodName(),
                request.headers().header("X-REQUEST-ID")
        );
        Instant now = Instant.now(Clock.systemUTC());
        Mono<Map<String, String>> mono = Mono.fromSupplier(() -> Map.of("path", "/api", "method", "get", "time", DateTimeFormatter.ISO_INSTANT.format(now)));
        return ServerResponse.ok()
                .header("content-type","application/json")
                .body(mono, new ParameterizedTypeReference<>() {});
    }
}
