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

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Optional;

class ApiPathAndApiKey {
    private final String path;
    private final String key;

    private ApiPathAndApiKey(String path, String key) {
        this.path = path;
        this.key = key;
    }

    static Optional<ApiPathAndApiKey> from(ServerHttpRequest request) {
        return header(request.getHeaders())
                .map(key -> new ApiPathAndApiKey(request.getPath().value(), key));
    }

    private static Optional<String> header(HttpHeaders headers) {
        return Optional.ofNullable(headers.get("X-API-KEY"))
                .filter(list -> !list.isEmpty())
                .or(() -> Optional.ofNullable(headers.get("x-api-key")).filter(list -> !list.isEmpty()))
                .map(list -> list.get(0));

    }

    String rateLimiterKey() {
        return String.format("%s-%s", key, path);
    }
}
