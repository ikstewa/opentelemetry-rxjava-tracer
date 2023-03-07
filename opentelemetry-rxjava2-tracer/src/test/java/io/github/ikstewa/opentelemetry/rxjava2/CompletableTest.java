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
package io.github.ikstewa.opentelemetry.rxjava2;

import com.google.common.truth.Truth;
import com.google.common.truth.Truth8;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompletableTest extends RxTracerTestBase {

  private static final Logger LOG = LogManager.getLogger();

  @Test
  @Override
  @DisplayName("Simple wrap")
  void simpleWrap() {
    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(() -> LOG.trace("Calling step 1"))
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                            Subscribe: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @DisplayName("Simple wrap error")
  void simpleWrapError() {
    final var span = tracer.spanBuilder("Subscribe");

    final var completable =
        Completable.fromRunnable(
                () -> {
                  LOG.trace("Calling step 1");
                  throw new RuntimeException("failed");
                })
            .compose(RxTracer.traceCompletable(span));
    Assertions.assertThrows(RuntimeException.class, completable::blockingAwait);

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                            Subscribe: [status: ERROR, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @DisplayName("Nested traces")
  void nestedTraces() {
    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(() -> LOG.trace("Calling step"))
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 1")))
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 2")))
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 3")))
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                Subscribe: [status: UNSET, attributes: []]
                  Step 3: [status: UNSET, attributes: []]
                    Step 2: [status: UNSET, attributes: []]
                      Step 1: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @DisplayName("Map nests trace")
  @Disabled("No map for completable")
  void mapNestsTrace() {}

  @Test
  @Override
  void concat() {
    final var span = tracer.spanBuilder("Subscribe");

    final var step1 =
        Completable.fromRunnable(() -> LOG.trace("Calling step"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 1")));
    final var step2 =
        Completable.fromRunnable(() -> LOG.trace("Calling step"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 2")));

    Completable.concat(List.of(step1, step2))
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                            Subscribe: [status: UNSET, attributes: []]
                              Step 1: [status: UNSET, attributes: []]
                              Step 2: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @Disabled("Concat eager doesn't apply to completable")
  void concat_eager() {}

  @Test
  @Override
  void with_delays() {
    final var span = tracer.spanBuilder("Subscribe");

    final var step1 =
        Completable.fromRunnable(() -> LOG.trace("Calling step 1"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 1")));
    final var step2 =
        Completable.fromRunnable(() -> LOG.trace("Calling step 2"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 2")));
    final var step3 =
        Completable.fromRunnable(() -> LOG.trace("Calling step 3"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 3")));

    step1
        .delay(10, TimeUnit.MILLISECONDS)
        .andThen(step2)
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Parent 1")))
        .andThen(step3)
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                  Subscribe: [status: UNSET, attributes: []]
                    Parent 1: [status: UNSET, attributes: []]
                      Step 1: [status: UNSET, attributes: []]
                      Step 2: [status: UNSET, attributes: []]
                    Step 3: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @Disabled("Does not apply to completable")
  void first_element() {}

  @Test
  @Override
  @DisplayName("Trace inside subscribe actual")
  void traceInsideSubscribeActual() {
    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(
            () -> {
              final var insideSpan = tracer.spanBuilder("Step 1").startSpan();
              try (var s = insideSpan.makeCurrent()) {
                LOG.trace("Calling step 1");
              } finally {
                insideSpan.end();
              }
            })
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                      Subscribe: [status: UNSET, attributes: []]
                        Step 1: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @Override
  @DisplayName("SubscribeOn")
  void subscribeOn() {
    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(() -> LOG.trace("Calling step 1"))
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 1")))
        .subscribeOn(Schedulers.io())
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
                Subscribe: [status: UNSET, attributes: []]
                  Step 1: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);

    // Validate onSubscribe event
    final var parentThread = findSubscribeThread("Subscribe");
    final var innerThread = findSubscribeThread("Step 1");
    Truth8.assertThat(parentThread).isPresent();
    Truth8.assertThat(parentThread).isNotEqualTo(innerThread);
  }

  @Test
  @Override
  @DisplayName("SubscribeOn no scheduler")
  void subscribeOnNoScheduler() {
    // Disabling the scheduler feature does not parent spans
    final var assembly = RxTracingAssembly.builder().setEnableSchedulerPropagation(false).build();
    assembly.disable().enable();

    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(() -> LOG.trace("Calling step 1"))
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Step 1")))
        .subscribeOn(Schedulers.io())
        .compose(RxTracer.traceCompletable(span))
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
            Subscribe: [status: UNSET, attributes: []]
            Step 1: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);

    assembly.disable();
  }

  @Test
  @Override
  @DisplayName("Spans are handled with dispose")
  void spansAreHandledWithDispose() {
    final var span = tracer.spanBuilder("Subscribe");

    final var disposable =
        Completable.fromRunnable(() -> LOG.trace("Calling step 1"))
            .delay(1, TimeUnit.SECONDS)
            .compose(RxTracer.traceCompletable(span))
            .subscribe(
                () -> Assertions.fail("Did not expect to complete"),
                err -> Assertions.fail("Did not expect to complete with error"));

    // Cancel the operation
    disposable.dispose();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
            Subscribe: [status: UNSET, attributes: [rxjava.canceled=true, span.cancelled=true]]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }
}
