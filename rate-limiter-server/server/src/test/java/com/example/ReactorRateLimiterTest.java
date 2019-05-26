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

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

class ReactorRateLimiterTest {

    private static final Logger logger = LoggerFactory.getLogger(ReactorRateLimiterTest.class);

    @Test
    void test() throws Exception {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(5))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(10L))
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter rateLimiter = registry.rateLimiter("test");

        RateLimiterOperator<String> operator = RateLimiterOperator.of(rateLimiter);

        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = Flux.interval(Duration.ofMillis(500L))
                .take(20L)
                .map(id -> "pass: id: " + id + ", time: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .flatMap(string -> Mono.from(operator.apply(Mono.just(string)))
                        .onErrorResume(RequestNotPermitted.class, requestNotPermitted -> Mono.just("error: " + requestNotPermitted.getMessage()))
                )
                .doOnComplete(latch::countDown)
                .subscribe(logger::info, e -> logger.info("error: {}", e.getClass().getSimpleName()));

        try(AutoCloseable ignored = disposable::dispose) {
            latch.await();
        }
    }
}
