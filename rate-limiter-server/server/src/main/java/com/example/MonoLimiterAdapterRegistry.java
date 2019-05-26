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
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Component
public class MonoLimiterAdapterRegistry {

    private final RateLimiterRegistry rateLimiterRegistry;

    MonoLimiterAdapterRegistry(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    <T> UnaryOperator<Mono<T>> getAdapter(String key) {
        Supplier<RateLimiter> rateLimiterSupplier = () -> rateLimiterRegistry.rateLimiter(key);
        return mono -> Mono.from(RateLimiterOperator.<T>of(rateLimiterSupplier.get()).apply(mono));
    }
}
