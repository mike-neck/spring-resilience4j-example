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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterService {

    private final WebClient webClient;
    private final String appId;

    public RateLimiterService(WebClient webClient, @Value("app.id") String appId) {
        this.webClient = webClient;
        this.appId = appId;
    }

    Mono<Boolean> isAcceptable(String userApiKey) {
        return webClient.post()
                .uri("/rate")
                .accept(MediaType.APPLICATION_JSON)
                .header("X-USER-API-KEY", userApiKey)
                .header("X-APP-ID", appId)
                .exchange()
                .filter(clientResponse -> clientResponse.statusCode().is2xxSuccessful())
                .flatMap(response -> response.bodyToMono(Acceptance.class))
                .filter(acceptance -> acceptance.accept)
                .hasElement();
    }

    @SuppressWarnings("WeakerAccess")
    public static class Acceptance {
        public boolean accept;
    }
}
