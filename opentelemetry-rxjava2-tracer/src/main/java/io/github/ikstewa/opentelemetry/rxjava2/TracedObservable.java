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
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.disposables.DisposableHelper;
import java.util.concurrent.atomic.AtomicReference;

class TracedObservable<T> extends Observable<T> {

  private final ObservableSource<T> source;
  private final SpanBuilder spanBuilder;

  public TracedObservable(ObservableSource<T> source, SpanBuilder span) {
    this.source = source;
    this.spanBuilder = span;
  }

  @Override
  protected void subscribeActual(Observer<? super T> observer) {
    final var span = RxTracer.spanSubscribe(spanBuilder);
    try (var ignored = span.makeCurrent()) {
      source.subscribe(new TracedObserver<>(observer, span));
    }
  }

  static final class TracedObserver<T> extends AtomicReference<Disposable>
      implements Observer<T>, Disposable {

    private final Observer<T> actualObserver;
    private final Span span;

    public TracedObserver(Observer<T> observer, Span span) {
      this.actualObserver = observer;
      this.span = span;
    }

    @Override
    public void onSubscribe(@NonNull Disposable d) {
      if (DisposableHelper.setOnce(this, d)) {
        actualObserver.onSubscribe(this);
      }
    }

    @Override
    public void onNext(@NonNull T t) {
      actualObserver.onNext(t);
    }

    @Override
    public void onError(@NonNull Throwable e) {
      RxTracer.spanError(span, e);

      actualObserver.onError(e);
    }

    @Override
    public void onComplete() {
      RxTracer.spanComplete(span);

      actualObserver.onComplete();
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
