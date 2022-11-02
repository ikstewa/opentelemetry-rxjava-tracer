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

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

abstract class OpenTelemetryTestBase {

  protected final InMemorySpanExporter testExporter = InMemorySpanExporter.create();
  protected final SdkTracerProvider tracerProvider;
  protected final Tracer tracer;

  OpenTelemetryTestBase() {
    final var resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.of(
                        ResourceAttributes.SERVICE_NAME, this.getClass().getSimpleName())));

    final var tracerBuilder =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(testExporter));

    if (Boolean.parseBoolean(System.getProperty("localJaeger"))) {
      tracerBuilder.addSpanProcessor(
          BatchSpanProcessor.builder(JaegerThriftSpanExporter.builder().build()).build());
    }

    tracerProvider = tracerBuilder.build();
    tracer = tracerProvider.get(this.getClass().getCanonicalName());
  }

  @AfterEach
  void flush_exporter() {
    tracerProvider.close();
  }

  public List<SpanData> getSpans() {
    return testExporter.getFinishedSpanItems().stream()
        .sorted(Comparator.comparing(SpanData::getStartEpochNanos))
        .toList();
  }

  public String printSpans() {
    return printSpans(new StringBuilder(), 0, SpanContext.getInvalid()).toString();
  }

  private StringBuilder printSpans(StringBuilder builder, int depth, SpanContext parent) {
    getSpans().stream()
        .filter(s -> s.getParentSpanId().equals(parent.getSpanId()))
        .forEach(s -> printSpan(builder, depth, s));
    return builder;
  }

  private void printSpan(StringBuilder builder, int depth, SpanData span) {
    if (!builder.isEmpty()) {
      builder.append("%n".formatted());
    }

    builder.append("  ".repeat(depth));
    builder.append(
        String.format(
            "%s: [status: %s, attributes: %s]",
            span.getName(),
            span.getStatus().getStatusCode(),
            span.getAttributes().asMap().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getKey()))
                .toList()));

    // print children
    printSpans(builder, depth + 1, span.getSpanContext());
  }
}
