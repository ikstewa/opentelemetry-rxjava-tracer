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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.CompletableTransformer;
import io.reactivex.rxjava3.core.MaybeSource;
import io.reactivex.rxjava3.core.MaybeTransformer;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.ObservableTransformer;
import io.reactivex.rxjava3.core.SingleSource;
import io.reactivex.rxjava3.core.SingleTransformer;

public final class RxTracer {

  private static final AttributeKey<Boolean> SPAN_CANCELLED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("span.cancelled");
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("rxjava.canceled");

  private RxTracer() {}

  public static CompletableSource traceCompletable(CompletableSource upstream, SpanBuilder span) {
    return new TracedCompletable(upstream, span);
  }

  public static CompletableTransformer traceCompletable(SpanBuilder span) {
    return upstream -> traceCompletable(upstream, span);
  }

  public static <T> SingleSource<T> traceSingle(SingleSource<T> upstream, SpanBuilder span) {
    return new TracedSingle<>(upstream, span);
  }

  public static <T> SingleTransformer<T, T> traceSingle(SpanBuilder span) {
    return upstream -> traceSingle(upstream, span);
  }

  public static <T> MaybeSource<T> traceMaybe(MaybeSource<T> upstream, SpanBuilder span) {
    return new TracedMaybe<>(upstream, span);
  }

  public static <T> MaybeTransformer<T, T> traceMaybe(SpanBuilder span) {
    return upstream -> traceMaybe(upstream, span);
  }

  public static <T> ObservableSource<T> traceObservable(
      ObservableSource<T> upstream, SpanBuilder span) {
    return new TracedObservable<>(upstream, span);
  }

  public static <T> ObservableTransformer<T, T> traceObservable(SpanBuilder span) {
    return upstream -> traceObservable(upstream, span);
  }

  // --------------------------------------------------------------------------------
  // Helper methods

  static void spanComplete(Span span) {
    span.end();
  }

  static void spanError(Span span, Throwable e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    span.end();
  }

  static void spanDispose(Span span) {

    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/cfe4a223071f4897aef5d60a11a0837d491fffbf/instrumentation/rxjava/rxjava-3-common/library/src/main/java/io/opentelemetry/instrumentation/rxjava/v3/common/RxJava3AsyncOperationEndStrategy.java#L157-L159
    span.setAttribute(CANCELED_ATTRIBUTE_KEY, true);

    // Using old attribute as well
    span.setAttribute(SPAN_CANCELLED_ATTRIBUTE_KEY, true);

    span.end();
  }
}
