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
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class GatewayAppClient {

    public static void main(String[] args) throws InterruptedException {
        List<User> users = List.of(
                user("api-foo", 6),
                user("api-bar", 5),
                user("api-baz", 8),
                user("api-qux", 3),
                user("api-waldo", 10)
        );

        Map<String, Logger> loggers = users.stream()
                .map(user -> user.apiKey)
                .map(LoggerFactory::getLogger)
                .collect(Collectors.toMap(Logger::getName, l -> l));

        HttpClient httpClient = HttpClient.create()
                .baseUrl("http://localhost:8080/api");

        List<Disposable> disposables = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(users.size());

        for (User user : users) {
            Disposable disposable = Flux.interval(Duration.ofSeconds(user.intervalSec))
                    .take(120L / user.intervalSec)
                    .flatMap(l -> httpClient
                            .headers(hs -> hs.add("X-USER-API-KEY", user.apiKey))
                            .get()
                            .response((httpClientResponse, byteBufFlux) ->
                                    byteBufFlux
                                            .aggregate()
                                            .asString(StandardCharsets.UTF_8)
                                            .map(str -> resp(httpClientResponse.status().code(), str))))
                    .doOnComplete(latch::countDown)
                    .subscribe(resp -> loggers.get(user.apiKey).info("response: {}", resp));
            disposables.add(disposable);
        }
        Disposable.Composite disposable = Disposables.composite(disposables);
        latch.await();
        disposable.dispose();
    }

    private static User user(final String apiKey, final int intervalSec) {
        return new User(apiKey, intervalSec);
    }

    public static class User {
        final String apiKey;
        final int intervalSec;

        User(String apiKey, int intervalSec) {
            this.apiKey = apiKey;
            this.intervalSec = intervalSec;
        }
    }

    private static Resp resp(int status, String json) {
        return new Resp(status, json);
    }

    public static class Resp {
        final int status;
        final String json;
        final Instant now;

        @SuppressWarnings("StringBufferReplaceableByString")
        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("{");
            sb.append("status=").append(status);
            sb.append(", json='").append(json).append('\'');
            sb.append(", now=").append(DateTimeFormatter.ISO_INSTANT.format(now));
            sb.append('}');
            return sb.toString();
        }

        Resp(int status, String json) {
            this.status = status;
            this.json = json;
            this.now = Instant.now();
        }
    }
}
