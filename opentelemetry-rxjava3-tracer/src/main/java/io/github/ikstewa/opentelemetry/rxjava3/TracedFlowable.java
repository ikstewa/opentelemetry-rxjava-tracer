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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.internal.subscriptions.SubscriptionHelper;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class TracedFlowable<T> extends Flowable<T> {

  private final Flowable<T> source;
  private final SpanBuilder spanBuilder;

  public TracedFlowable(Flowable<T> source, SpanBuilder span) {
    this.source = source;
    this.spanBuilder = span;
  }

  @Override
  protected void subscribeActual(@NonNull Subscriber<? super T> observer) {
    final var span = spanBuilder.startSpan();
    try (var ignored = span.makeCurrent()) {
      source.subscribe(new TracedSubscriber<>(observer, span));
    }
  }

  static final class TracedSubscriber<T> implements FlowableSubscriber<T>, Subscription {

    private final Subscriber<? super T> downstream;
    private final Span span;
    Subscription upstream;

    public TracedSubscriber(Subscriber<T> observer, Span span) {
      this.downstream = observer;
      this.span = span;
    }

    @Override
    public void onSubscribe(Subscription s) {
      if (SubscriptionHelper.validate(this.upstream, s)) {
        this.upstream = s;
        downstream.onSubscribe(this);
      }
    }

    @Override
    public void onNext(@NonNull T t) {
      downstream.onNext(t);
    }

    @Override
    public void onError(@NonNull Throwable e) {
      if (upstream != SubscriptionHelper.CANCELLED) {
        RxTracer.spanError(span, e);

        downstream.onError(e);
      } else {
        RxJavaPlugins.onError(e);
      }
    }

    @Override
    public void onComplete() {
      if (upstream != SubscriptionHelper.CANCELLED) {
        RxTracer.spanComplete(span);

        downstream.onComplete();
      }
    }

    @Override
    public void request(long n) {
      upstream.request(n);
    }

    @Override
    public void cancel() {
      if (upstream != SubscriptionHelper.CANCELLED) {
        upstream.cancel();
        upstream = SubscriptionHelper.CANCELLED;

        RxTracer.spanDispose(span);
      }
    }
  }
}
