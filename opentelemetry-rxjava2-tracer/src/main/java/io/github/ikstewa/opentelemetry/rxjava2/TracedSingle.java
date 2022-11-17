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
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import java.util.concurrent.atomic.AtomicReference;

class TracedSingle<T> extends Single<T> {

  private final SingleSource<T> source;
  private final SpanBuilder spanBuilder;

  public TracedSingle(SingleSource<T> source, SpanBuilder span) {
    this.source = source;
    this.spanBuilder = span;
  }

  @Override
  protected void subscribeActual(SingleObserver<? super T> observer) {
    final var span = RxTracer.spanSubscribe(spanBuilder);
    try (var ignored = span.makeCurrent()) {
      source.subscribe(new TracedSingleObserver<>(observer, span));
    }
  }

  private static final class TracedSingleObserver<T> extends AtomicReference<Disposable>
      implements SingleObserver<T>, Disposable {

    private final SingleObserver<T> actualObserver;
    private final Span span;

    public TracedSingleObserver(SingleObserver<T> observer, Span span) {
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
