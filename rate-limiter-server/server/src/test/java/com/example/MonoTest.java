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
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.CountDownLatch;

class MonoTest {

    @Test
    void compose() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Disposable disposable = Mono.just("test")
                .map(String::toUpperCase)
                .log()
                .compose(mono -> Mono.error(new Exception("test-error")))
                .doOnSuccessOrError((success, error) -> latch.countDown())
                .subscribe(System.out::println, System.out::println);

        try (AutoCloseable ignored = disposable::dispose) {
            latch.await();
        }
    }
}
