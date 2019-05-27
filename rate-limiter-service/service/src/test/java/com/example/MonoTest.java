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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.regex.Pattern;

class MonoTest {

    @Test
    void empty() {
        Pattern pattern = Pattern.compile("^t");
        Mono<String> mono = Mono.just("test")
                .filter(pattern.asMatchPredicate())
                .defaultIfEmpty("foo")
                .onErrorReturn("bar");
        StepVerifier.create(mono)
                .expectNext("foo")
                .verifyComplete();
    }
}
