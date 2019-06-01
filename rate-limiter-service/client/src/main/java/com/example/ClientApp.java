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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ClientApp {

    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);

    private static final Map<String, Long> userTokens = Map.of(
            "user-1-token", 6L,
            "user-2-token", 5L,
            "user-3-token", 7L,
            "user-4-token", 3L,
            "user-5-token", 10L);

    private static final List<String> servers = List.of(
            "http://localhost:8081/api", "http://localhost:8082/api"
    );

    public static void main(String[] args) throws Exception {
        Flux<String> requestPace = userTokens.entrySet()
                .stream()
                .map(entry -> pace(entry.getKey(), entry.getValue()))
                .reduce(Flux.empty(), Flux::mergeWith)
                .take(100L);

        Map<String, HttpClient> httpClients = servers.stream()
                .collect(Collectors.toMap(it -> it, HttpClient.create()::baseUrl));

        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = requestPace
                .map(token -> Tuples.of(token, randomOf(servers)))
                .map(p -> Tuples.of(p, httpClients.get(p.getT2())))
                .flatMap(ClientApp::callHttp)
                .map(p -> Tuples.of(p.getT1().getT1(), p.getT1().getT2(), p.getT2().getT2(), p.getT2().getT1()))
                .doOnComplete(latch::countDown)
                .subscribe(p -> logger.info("api-request: token: {}, server: {}, status: {}, response-body: {}", p.getT1(), p.getT2(), p.getT3(), p.getT4()));

        try (AutoCloseable ignored = disposable::dispose) {
            latch.await();
        }
    }

    private static Flux<Tuple2<Tuple2<String, String>, Tuple2<String, HttpResponseStatus>>> callHttp(Tuple2<Tuple2<String, String>, HttpClient> p) {
        return callHttp(p.getT2(), p.getT1().getT1())
                .map(res -> Tuples.of(p.getT1(), res));
    }

    private static Flux<Tuple2<String, HttpResponseStatus>> callHttp(HttpClient httpClient, String userToken) {
        return httpClient.headers(hs -> hs.add("X-USER-API-KEY", userToken))
                .get()
                .response((httpClientResponse, byteBufFlux) -> byteBufFlux.aggregate().asString(StandardCharsets.UTF_8).zipWith(Mono.just(httpClientResponse.status())));
    }

    private static Flux<String> pace(String token, Long interval) {
        return Flux.interval(Duration.ofSeconds(interval))
                .map(l -> token);
    }

    private static <T> T randomOf(List<? extends T> list) {
        int index = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(index);
    }
}
