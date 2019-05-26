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
import io.vavr.control.Try;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@ExtendWith(RateLimiterTest.Params.class)
class RateLimiterTest {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterTest.class);

    static class Params implements ParameterResolver {
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            Class<?> type = parameterContext.getParameter().getType();
            return type.isAssignableFrom(RateLimiterConfig.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return RateLimiterConfig.custom()
                    .limitRefreshPeriod(Duration.ofSeconds(2))
                    .limitForPeriod(2)
                    .timeoutDuration(Duration.ofMillis(100L))
                    .build();
        }
    }

    @Test
    void rate_1perSec(RateLimiterConfig config) throws Exception {
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(config);

        RateLimiter rateLimiterFirst = rateLimiterRegistry.rateLimiter("test-first");

        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = Flux.interval(Duration.ofMillis(500L))
                .take(10)
                .map(l -> LocalDateTime.now())
                .<Runnable>map(localDateTime -> () -> logger.info("time: {}", localDateTime))
                .map(runnable -> RateLimiter.decorateCheckedRunnable(rateLimiterFirst, runnable::run))
                .doOnComplete(latch::countDown)
                .subscribe(runnable -> Try.run(runnable).onFailure(e -> logger.info("error", e)));

        try (AutoCloseable ignored = disposable::dispose) {
            latch.await();
        }
    }

    enum Names {
        EVEN,
        ODD,
        ;
    }

    @Test
    void rateLimiterWithFor(RateLimiterConfig config) throws InterruptedException {
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        List<Names> list = List.of(Names.EVEN, Names.ODD);
        for (int i = 0; i < 20; i++) {
            final int id = i;
            for (Names names : list) {
                RateLimiter rateLimiter = registry.rateLimiter(names.name());
                Runnable runnable = RateLimiter.decorateRunnable(
                        rateLimiter,
                        () -> logger.info("id: {}, name: {}", id, names));
                Try.runRunnable(runnable)
                        .onFailure(
                                e -> logger.info("error, id: {}, name: {}, error: {}",
                                        id,
                                        names,
                                        e.getClass().getSimpleName()));
            }
            Thread.sleep(100L);
        }
    }

    @Test
    void rate_withExtendedFor(RateLimiterConfig config) {
        RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(config);

        logger.info("=== start ===");
        for (int i = 0; i < 10; i++) {
            logger.info("start: {}", i);
            Arrays.stream(Names.values())
                    .map(Enum::name)
                    .map(name -> new Holder<>(name, LocalDateTime.now()))
                    .map(holder -> RateLimiter.decorateCheckedRunnable(rateLimiterRegistry.rateLimiter(holder.left), () -> logger.info("name: {}, time: {}", holder.left, holder.right)))
                    .forEach(runnable -> Try.run(runnable).onFailure(e -> logger.info("error", e)).stdout());
            logger.info("start: {}", i);
        }
        logger.info("=== end ===");
    }

    static class Holder<L, R> {
        final L left;
        final R right;

        Holder(L left, R right) {
            this.left = left;
            this.right = right;
        }
    }

    static String name(long l) {
        return l % 2 == 0 ? "even" : "odd";
    }

    @Test
    void rateLimiterOnDemand(RateLimiterConfig config) throws Exception {
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);

        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = Flux.interval(Duration.ofMillis(100L))
                .take(20)
                .map(RateLimiterTest::name)
                .map(name -> new Holder<>(name, LocalDateTime.now()))
                .map(holder -> RateLimiter.decorateCheckedRunnable(registry.rateLimiter(holder.left), () -> logger.info("name: {}, time: {}", holder.left, holder.right)))
                .doOnComplete(latch::countDown)
                .subscribe(runnable -> Try.run(runnable).onFailure(e -> logger.info("error", e)));

        try (AutoCloseable ignored = disposable::dispose) {
            latch.await();
        }
    }

    @Test
    void anotherWay() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(300L))
                .limitRefreshPeriod(Duration.ofSeconds(2))
                .limitForPeriod(2)
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);

        logger.info("=== start ===");
        for (int i = 0; i < 10; i++) {
            logger.info("start: {}", i);
            Arrays.stream(Names.values())
                    .map(Enum::name)
                    .map(name -> new Holder<>(name, LocalDateTime.now()))
                    .map(holder -> RateLimiter.decorateCheckedRunnable(registry.rateLimiter(holder.left), () -> logger.info("name: {}, time: {}", holder.left, holder.right)))
                    .forEach(runnable -> Try.run(runnable).onFailure(e -> logger.info("error", e)).stdout());
            logger.info("start: {}", i);
        }
        logger.info("=== end ===");
    }
}
