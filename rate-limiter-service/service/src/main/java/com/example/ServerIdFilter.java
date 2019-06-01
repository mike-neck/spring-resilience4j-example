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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(-1)
public class ServerIdFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(ServerIdFilter.class);

    private static final Exception FORBIDDEN_EXCEPTION = new Exception("forbidden");

    private final Jackson2JsonEncoder jsonEncoder;

    public ServerIdFilter(Jackson2JsonEncoder jsonEncoder) {
        this.jsonEncoder = jsonEncoder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();
        List<String> appIds = headers.get("X-APP-ID");
        return Mono.justOrEmpty(appIds)
                .filter(ids -> !ids.isEmpty())
                .switchIfEmpty(Mono.error(FORBIDDEN_EXCEPTION))
                .doOnError(e -> logger.info(
                        "filter forbidden - missing api-key, id: {}, address: {}, x-app-ids: {}",
                        request.getId(),
                        request.getRemoteAddress(),
                        appIds))
                .then(Mono.defer(() -> chain.filter(exchange)))
                .onErrorResume(e -> forbidden(exchange, request, appIds));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, ServerHttpRequest request, List<String> appIds) {
        return Mono.defer(() -> {
            logger.info(
                    "forbidden - missing api-key, id: {}, address: {}, x-app-ids: {}",
                    request.getId(),
                    request.getRemoteAddress(),
                    appIds);
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return RequestAcceptance.FORBIDDEN
                    .writeResponse(response, jsonEncoder);
        });
    }
}
