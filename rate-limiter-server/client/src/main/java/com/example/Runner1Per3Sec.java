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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class Runner1Per3Sec {

    private static final Logger logger = LoggerFactory.getLogger(Runner1Per3Sec.class);

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = HttpClient.create();
        HttpClient client = httpClient.baseUrl("http://localhost:8080");

        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = Flux.interval(Duration.ofSeconds(3L))
                .take(30L)
                .flatMap(l -> toResult(l,
                        client.headers(headers ->
                                headers.add("X-API-KEY", "test")
                                        .add("X-REQUEST-ID", l))
                        .get().uri("/api")))
                .doOnComplete(latch::countDown)
                .doOnError(e -> latch.countDown())
                .subscribe(result ->
                        logger.info("id: {}, status: {}, body: {}",
                                result.id, result.status, result.response));

        try (AutoCloseable ignore = disposable::dispose) {
            latch.await();
        }
    }

    private static Mono<Result> toResult(long id, HttpClient.ResponseReceiver<?> receiver) {
        return receiver.responseSingle((response, byteBufMono) ->
                byteBufMono.asString(StandardCharsets.UTF_8)
                        .map(json -> new Result(id, response.status().code(), json)));
    }

    static class Result {
        final long id;
        final int status;
        final String response;

        Result(long id, int status, String response) {
            this.id = id;
            this.status = status;
            this.response = response;
        }
    }
}
