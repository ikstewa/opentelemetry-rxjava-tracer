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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests ran for README examples
 *
 * <p>run Jaeger locally with:
 *
 * <pre>
 * docker run -d --name jaeger \
 *   -p 5775:5775/udp \
 *   -p 6831:6831/udp \
 *   -p 6832:6832/udp \
 *   -p 5778:5778 \
 *   -p 16686:16686 \
 *   -p 14268:14268 \
 *   -p 9411:9411 \
 *   jaegertracing/all-in-one:1.6
 * </pre>
 */
@Disabled
class DocSamplesTest {

  private static final Logger LOG = LogManager.getLogger();

  private static Tracer tracer;

  @BeforeAll
  static void setup() {
    Resource resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.of(
                        ResourceAttributes.SERVICE_NAME, DocSamplesTest.class.getSimpleName())));

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(
                SimpleSpanProcessor.create(JaegerThriftSpanExporter.builder().build()))
            .setResource(resource)
            .build();

    OpenTelemetry openTelemetry =
        OpenTelemetrySdk.builder().setTracerProvider(sdkTracerProvider).buildAndRegisterGlobal();

    tracer = openTelemetry.getTracer("instrumentation-library-name", "1.0.0");
  }

  @Test
  @DisplayName("Default tracing")
  void defaultTracing() throws InterruptedException {
    final Completable operation1 =
        Completable.fromRunnable(
            () -> {
              Span execute = tracer.spanBuilder("Execute 1").startSpan();
              try (var ss = execute.makeCurrent()) {
                LOG.trace("Executing step 1");
              } finally {
                execute.end();
              }
            });
    final Completable operation2 =
        Completable.fromRunnable(
            () -> {
              Span execute = tracer.spanBuilder("Execute 2").startSpan();
              try (var ss = execute.makeCurrent()) {
                LOG.trace("Executing step 2");
              } finally {
                execute.end();
              }
            });

    var latch = new CountDownLatch(1);

    Span subscribe = tracer.spanBuilder("Subscribe").startSpan();
    try (var s = subscribe.makeCurrent()) {
      Completable.concatArray(operation1, operation2).subscribe(latch::countDown);
    } finally {
      subscribe.end();
    }

    latch.await();
  }

  void longRunningOperation() {
    Span execute = tracer.spanBuilder("LongRunningOperation").startSpan();
    try (var s = execute.makeCurrent()) {
      LOG.info("Starting my long running operation");
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      LOG.info("Finished my long running operation");
    } finally {
      execute.end();
    }
  }

  @Test
  @DisplayName("Long running operation")
  void longRunningOperationTest() throws InterruptedException {
    // RxTracingAssembly.builder().setEnableSchedulerPropagation(true).build().enable();

    final var span = tracer.spanBuilder("SubscribeToLongRunning");

    var latch = new CountDownLatch(1);

    Completable.fromRunnable(this::longRunningOperation)
        .subscribeOn(Schedulers.io())
        .compose(RxTracer.traceCompletable(span))
        .subscribe(latch::countDown);

    latch.await();
  }
}
