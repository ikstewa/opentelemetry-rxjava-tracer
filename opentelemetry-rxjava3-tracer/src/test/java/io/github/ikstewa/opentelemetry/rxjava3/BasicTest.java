//
// Copyright 2022 Ian Stewart
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package io.github.ikstewa.opentelemetry.rxjava3;

import com.google.common.truth.Truth;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.rxjava.v3_1_1.TracingAssembly;
import io.reactivex.rxjava3.core.Completable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests some basic assumptions with async tracing and how parent spans work. */
class BasicTest extends OpenTelemetryTestBase {

  private static final Logger LOG = LogManager.getLogger();

  @Test
  @DisplayName("Parent span comes from subscribe blocking")
  void parentSpanComesFromSubscribeBlocking() {
    final Completable operation;

    Span assemble = tracer.spanBuilder("Assemble").startSpan();
    try (var s = assemble.makeCurrent()) {
      operation =
          Completable.fromRunnable(
              () -> {
                Span execute = tracer.spanBuilder("Execute").startSpan();
                try (var ss = execute.makeCurrent()) {
                  LOG.trace("Executing step");
                } finally {
                  execute.end();
                }
              });

    } finally {
      assemble.end();
    }

    Span subscribe = tracer.spanBuilder("Subscribe").startSpan();
    try (var s = subscribe.makeCurrent()) {
      operation.blockingAwait();
    } finally {
      subscribe.end();
    }

    LOG.debug(printSpans());
    final String expectedSpans =
        """
                    Assemble: [status: UNSET, attributes: []]
                    Subscribe: [status: UNSET, attributes: []]
                      Execute: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @DisplayName("Async subscription without TracingAssembly does not parent")
  void asyncSubscriptionWithoutTracingAssemblyDoesNotParent() throws InterruptedException {
    // Disable OpenTelemetry RxJava instrumentation
    TracingAssembly.builder().build().disable();

    final var latch = new CountDownLatch(1);

    Span subscribe = tracer.spanBuilder("Subscribe").startSpan();
    try (var s = subscribe.makeCurrent()) {
      Completable.complete()
          .delay(10, TimeUnit.MILLISECONDS)
          .andThen(
              Completable.fromRunnable(
                  () -> {
                    Span execute = tracer.spanBuilder("Execute").startSpan();
                    try (var ss = execute.makeCurrent()) {
                      LOG.trace("Executing step");
                    } finally {
                      execute.end();
                    }
                  }))
          .subscribe(latch::countDown, Assertions::fail);
    } finally {
      subscribe.end();
    }
    latch.await();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                            Subscribe: [status: UNSET, attributes: []]
                            Execute: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @DisplayName("Async subscription with TracingAssembly parents")
  void asyncSubscriptionWithTracingAssemblyParents() throws InterruptedException {
    // Enable OpenTelemetry RxJava instrumentation
    TracingAssembly.builder().build().enable();

    final var latch = new CountDownLatch(1);

    Span subscribe = tracer.spanBuilder("Subscribe").startSpan();
    try (var s = subscribe.makeCurrent()) {
      Completable.complete()
          .delay(10, TimeUnit.MILLISECONDS)
          .andThen(
              Completable.fromRunnable(
                  () -> {
                    Span execute = tracer.spanBuilder("Execute").startSpan();
                    try (var ss = execute.makeCurrent()) {
                      LOG.trace("Executing step");
                    } finally {
                      execute.end();
                    }
                  }))
          .subscribe(latch::countDown, Assertions::fail);
    } finally {
      subscribe.end();
    }
    latch.await();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                        Subscribe: [status: UNSET, attributes: []]
                          Execute: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);

    TracingAssembly.builder().build().disable();
  }
}
