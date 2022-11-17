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
import com.google.common.truth.Truth8;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleTest extends RxTracerTestBase {

  private static final Logger LOG = LogManager.getLogger();

  @Test
  @DisplayName("Simple wrap")
  void simpleWrap() {
    final var span = tracer.spanBuilder("Subscribe");

    Single.fromCallable(() -> "step1")
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans = """
              Subscribe: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @DisplayName("Simple wrap error")
  void simpleWrapError() {
    final var span = tracer.spanBuilder("Subscribe");

    final var operation =
        Single.fromCallable(
                () -> {
                  LOG.trace("Calling step 1");
                  throw new RuntimeException("failed");
                })
            .compose(RxTracer.traceSingle(span))
            .ignoreElement();
    Assertions.assertThrows(RuntimeException.class, operation::blockingAwait);

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans = """
              Subscribe: [status: ERROR, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @DisplayName("Nested traces")
  void nestedTraces() {
    final var span = tracer.spanBuilder("Subscribe");

    Single.fromCallable(() -> "step1")
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")))
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 2")))
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 3")))
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  @DisplayName("Map nests trace")
  void mapNestsTrace() {
    final var span = tracer.spanBuilder("Subscribe");

    Single.just("before")
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")))
        .map(
            a -> {
              final var mapSpan = tracer.spanBuilder("MapOperation").startSpan();
              try (final var s = mapSpan.makeCurrent()) {
                return "after";
              } finally {
                mapSpan.end();
              }
            })
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
              Subscribe: [status: UNSET, attributes: []]
                Step 1: [status: UNSET, attributes: []]
                MapOperation: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  void concat() {
    final var span = tracer.spanBuilder("Subscribe");

    final var step1 =
        Single.fromCallable(() -> "step1")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")));
    final var step2 =
        Single.fromCallable(() -> "step2")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 2")));

    Single.concat(List.of(step1, step2))
        .toList()
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  void concat_eager() {
    final var span = tracer.spanBuilder("Subscribe");

    final var step1 =
        Single.fromCallable(() -> "step1")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")));
    final var step2 =
        Single.fromCallable(() -> "step2")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 2")));

    Single.concatEager(List.of(step1, step2))
        .toList()
        .doOnSuccess(result -> span.setAttribute("result", result.toString()))
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  void with_delays() {
    final var span = tracer.spanBuilder("Subscribe");

    final var step1 =
        Single.fromCallable(() -> "step1")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")));
    final var step2 =
        Single.fromCallable(() -> "step2")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 2")));
    final var step3 =
        Single.fromCallable(() -> "step3")
            .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 3")));

    step1
        .delay(10, TimeUnit.MILLISECONDS)
        .flatMap(__ -> step2)
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Parent 1")))
        .flatMap(__ -> step3)
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  void first_element() {
    final var result =
        Observable.range(1, 100)
            .concatMapSingle(
                input -> {
                  final var span = tracer.spanBuilder("LongOperation." + input);
                  return Single.just(input)
                      .delay(10, TimeUnit.MILLISECONDS)
                      .compose(RxTracer.traceSingle(span));
                })
            .firstElement()
            .blockingGet();

    Truth.assertThat(result).isEqualTo(1);

    Truth.assertThat(getSpans()).hasSize(1);
  }

  @Test
  @DisplayName("Trace inside subscribe actual")
  void traceInsideSubscribeActual() {
    final var span = tracer.spanBuilder("Subscribe");

    Single.fromCallable(
            () -> {
              final var insideSpan = tracer.spanBuilder("Step 1").startSpan();
              try (var s = insideSpan.makeCurrent()) {
                LOG.trace("Calling step 1");
                return "step1";
              } finally {
                insideSpan.end();
              }
            })
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
        .blockingAwait();

    LOG.debug("\r\n{}", printSpans());
    final String expectedSpans =
        """
              Subscribe: [status: UNSET, attributes: []]
                Step 1: [status: UNSET, attributes: []]""";
    Truth.assertThat(printSpans()).isEqualTo(expectedSpans);
  }

  @Test
  @DisplayName("SubscribeOn")
  void subscribeOn() {
    final var span = tracer.spanBuilder("Subscribe");

    Single.fromCallable(() -> "step1")
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")))
        .subscribeOn(Schedulers.io())
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  @DisplayName("SubscribeOn no scheduler")
  void subscribeOnNoScheduler() {
    // Disabling the scheduler feature does not parent spans
    final var assembly = RxTracingAssembly.builder().setEnableSchedulerPropagation(false).build();
    assembly.disable().enable();

    final var span = tracer.spanBuilder("Subscribe");

    Single.fromCallable(() -> "step1")
        .compose(RxTracer.traceSingle(tracer.spanBuilder("Step 1")))
        .subscribeOn(Schedulers.io())
        .compose(RxTracer.traceSingle(span))
        .ignoreElement()
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
  @DisplayName("Spans are handled with dispose")
  void spansAreHandledWithDispose() {
    final var span = tracer.spanBuilder("Subscribe");

    final var disposable =
        Single.fromCallable(() -> "step1")
            .delay(1, TimeUnit.SECONDS)
            .compose(RxTracer.traceSingle(span))
            .subscribe(
                __ -> Assertions.fail("Did not expect to complete"),
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
