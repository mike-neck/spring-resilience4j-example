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

import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

class RateLimiterAdapterTest {

    private final RateLimiterServiceApp app = new RateLimiterServiceApp();

    @Test
    void test() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(2L))
                .limitForPeriod(1)
                .timeoutDuration(Duration.ofMillis(10L))
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);

        RateLimiterServiceApp.RateLimiterAdapterManager adapterManager = app.rateLimiterAdapter(registry);

        Flux<RequestAcceptance> flux = Flux.interval(Duration.ofMillis(100L))
                .take(3)
                .map(l -> RequestAcceptance.ACCEPT)
                .map(Mono::just)
                .flatMap(adapterManager.take("test")::apply);

        StepVerifier.create(flux)
                .expectNext(RequestAcceptance.ACCEPT)
                .expectNext(RequestAcceptance.REJECT)
                .expectNext(RequestAcceptance.REJECT)
                .verifyComplete();
    }
}
