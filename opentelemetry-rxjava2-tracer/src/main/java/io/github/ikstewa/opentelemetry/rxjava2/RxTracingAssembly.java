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

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import io.opentelemetry.instrumentation.rxjava.v2_0.TracingAssembly;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import javax.annotation.Nullable;

public final class RxTracingAssembly {

  @GuardedBy("RxTracer.class")
  @Nullable
  private static Function<? super Runnable, ? extends Runnable> oldScheduleHandler;

  @GuardedBy("RxTracer.class")
  private static boolean enabled;

  public static RxTracingAssemblyBuilder builder() {
    return new RxTracingAssemblyBuilder();
  }

  private final boolean enableSchedulerPropagation;

  RxTracingAssembly(boolean enableSchedulerPropagation) {
    this.enableSchedulerPropagation = enableSchedulerPropagation;
  }

  public RxTracingAssembly enable() {
    synchronized (RxTracingAssembly.class) {
      if (enabled) {
        return this;
      }

      TracingAssembly.builder()
          // Adds an attribute when span is cancelled via dispose
          .setCaptureExperimentalSpanAttributes(true)
          .build()
          .enable();

      // Pass context on to other threads
      oldScheduleHandler = RxJavaPlugins.getScheduleHandler();
      if (enableSchedulerPropagation) {
        RxJavaPlugins.setScheduleHandler(
            compose(
                oldScheduleHandler,
                runnable -> {
                  final var context = Context.current();
                  return () -> {
                    try (var ignored = context.makeCurrent()) {
                      runnable.run();
                    }
                  };
                }));
      }

      enabled = true;
    }
    return this;
  }

  public RxTracingAssembly disable() {
    synchronized (RxTracingAssembly.class) {
      if (!enabled) {
        return this;
      }

      // FIXME: This could potentially remove TracingAssembly if initialized separately
      TracingAssembly.create().disable();

      RxJavaPlugins.setScheduleHandler(oldScheduleHandler);
      oldScheduleHandler = null;

      enabled = false;
    }
    return this;
  }

  // --------------------------------------------------------------------------------
  // Helper methods

  private static <T> Function<? super T, ? extends T> compose(
      Function<? super T, ? extends T> before, Function<? super T, ? extends T> after) {
    if (before == null) {
      return after;
    }
    return (T v) -> after.apply(before.apply(v));
  }
}
