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

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

enum RequestAcceptance {
    ACCEPT(HttpStatus.OK, Map.of("accept", Boolean.TRUE)),
    REJECT(HttpStatus.OK, Map.of("accept", Boolean.FALSE)),
    FORBIDDEN(HttpStatus.FORBIDDEN, Map.of("accept", Boolean.FALSE)),
    ;

    final HttpStatus status;
    final Map<String, Boolean> body;

    RequestAcceptance(HttpStatus status, Map<String, Boolean> body) {
        this.status = status;
        this.body = body;
    }

    Flux<DataBuffer> writeJson(ServerHttpResponse response, Jackson2JsonEncoder jsonEncoder) {
        return jsonEncoder
                .encode(
                        Mono.just(this.body),
                        response.bufferFactory(),
                        ResolvableType.forType(new ParameterizedTypeReference<Map<String, Boolean>>() {
                        }),
                        MimeTypeUtils.APPLICATION_JSON,
                        Map.of());
    }

    Mono<Void> writeResponse(ServerHttpResponse response, Jackson2JsonEncoder jsonEncoder) {
        Flux<DataBuffer> data = writeJson(response, jsonEncoder);
        return response.writeWith(data);
    }
}
