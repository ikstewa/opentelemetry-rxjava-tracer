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

import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class RxTracerTestBase extends OpenTelemetryTestBase {

  private static final RxTracingAssembly ASSEMBLY =
      RxTracingAssembly.builder().setEnableSchedulerPropagation(true).build();

  @BeforeEach
  void setup_rxassembly() {
    ASSEMBLY.enable();
  }

  @AfterEach
  void cleanup_rxassembly() {
    ASSEMBLY.disable();
  }

  Optional<SpanData> findSpan(String spanName) {
    return getSpans().stream().filter(s -> s.getName().equals(spanName)).findFirst();
  }

  Optional<EventData> findSubscribeEvent(String spanName) {
    return findSpan(spanName)
        .flatMap(
            s -> s.getEvents().stream().filter(e -> e.getName().equals("onSubscribe")).findFirst());
  }

  Optional<Long> findSubscribeThread(String spanName) {
    return findSubscribeEvent(spanName)
        .map(e -> e.getAttributes().get(SemanticAttributes.THREAD_ID));
  }

  abstract void simpleWrap();

  abstract void simpleWrapError();

  abstract void nestedTraces();

  abstract void mapNestsTrace();

  abstract void concat();

  abstract void concat_eager();

  abstract void with_delays();

  abstract void first_element();

  abstract void traceInsideSubscribeActual();

  abstract void subscribeOn();

  abstract void subscribeOnNoScheduler();

  abstract void spansAreHandledWithDispose();
}
