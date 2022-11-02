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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.reactivex.Maybe;
import io.reactivex.MaybeObserver;
import io.reactivex.MaybeSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import java.util.concurrent.atomic.AtomicReference;

public class TracedMaybe<T> extends Maybe<T> {

  private final MaybeSource<T> source;
  private final SpanBuilder spanBuilder;

  public TracedMaybe(MaybeSource<T> source, SpanBuilder span) {
    this.source = source;
    this.spanBuilder = span;
  }

  @Override
  protected void subscribeActual(MaybeObserver<? super T> observer) {
    final var span = spanBuilder.startSpan();
    try (var ignored = span.makeCurrent()) {
      source.subscribe(new TracedMaybeObserver<>(observer, span));
    }
  }

  private static final class TracedMaybeObserver<T> extends AtomicReference<Disposable>
      implements MaybeObserver<T>, Disposable {

    private final MaybeObserver<T> actualObserver;
    private final Span span;

    public TracedMaybeObserver(MaybeObserver<T> observer, Span span) {
      this.actualObserver = observer;
      this.span = span;
    }

    @Override
    public void onSubscribe(Disposable d) {
      if (DisposableHelper.setOnce(this, d)) {
        actualObserver.onSubscribe(this);
      }
    }

    @Override
    public void onSuccess(@NonNull T t) {
      if (!isDisposed()) {
        RxTracer.spanComplete(span);

        actualObserver.onSuccess(t);
      }
    }

    @Override
    public void onError(Throwable e) {
      RxTracer.spanError(span, e);

      actualObserver.onError(e);
    }

    @Override
    public void onComplete() {
      if (!isDisposed()) {
        RxTracer.spanComplete(span);

        actualObserver.onComplete();
      }
    }

    @Override
    public void dispose() {
      DisposableHelper.dispose(this);

      RxTracer.spanDispose(span);
    }

    @Override
    public boolean isDisposed() {
      return DisposableHelper.isDisposed(get());
    }
  }
}
