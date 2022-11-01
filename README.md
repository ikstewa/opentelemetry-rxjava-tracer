# opentelemetry-rxjava-tracer
Utility methods to simplify tracing RxJava when using manual instrumentation.

![Build](https://github.com/ikstewa/opentelemetry-rxjava-tracer/workflows/Java%20CI%20with%20Gradle/badge.svg)
[![codecov.io](http://codecov.io/github/ikstewa/opentelemetry-rxjava-tracer/coverage.svg?branch=master)](http://codecov.io/github/ikstewa/opentelemetry-rxjava-tracer?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.ikstewa/opentelemetry-rxjava-tracer/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.ikstewa/opentelemetry-rxjava-tracer)

 
## RxJava 2
RxJava 2: [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava2/rxjava/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava2/rxjava)

```groovy
dependencies {
    implementation "io.github.ikstewa:opentelemetry-rxjava2-tracer:0.1.0"
}
```

## RxJava 3

RxJava 3: [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava3/rxjava/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.reactivex.rxjava3/rxjava)

```groovy
dependencies {
    implementation "io.github.ikstewa:opentelemetry-rxjava3-tracer:0.1.0"
}
```

# Features

In order to solve parenting issues with async operations the standard [OpenTelementry instrumentation for RxJava](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/rxjava)
adds support for propagating the OpenTelementry Context through observables by registering RxJava plugins using the
[TracingAssembly](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/rxjava/rxjava-3.0/library/src/main/java/io/opentelemetry/instrumentation/rxjava/v3_0/TracingAssembly.java).

## Simplified Interface

Using the standard OpenTelementry Tracer API for manual instrumentation can be cumbersome when working with RxJava.

Tracing a Completable might look something like:
```Java
class MyClass {
  private static final Tracer tracer =
    openTelemetry.getTracer("instrumentation-library-name", "1.0.0");
  void doWork() {
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

    Span subscribe = tracer.spanBuilder("Subscribe").startSpan();
    try (var s = subscribe.makeCurrent()) {
      Completable.concatArray(operation1, operation2).subscribe();
    } finally {
      subscribe.end();
    }
    // TODO: Add Jaeger trace screenshot
    // This will trace as:
    // - Subscribe
    // -   Execute 1
    // -   Execute 2
  }
}
```

Using the RxTracer this can be simplified to:

```Java
class MyClass {
  private static final Tracer tracer =
    openTelemetry.getTracer("instrumentation-library-name", "1.0.0");
  void doWork() {
    final Completable operation1 =
        Completable.fromRunnable(() -> LOG.trace("Executing step 1"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Execute 1")));
    final Completable operation2 =
        Completable.fromRunnable(() -> LOG.trace("Executing step 2"))
            .compose(RxTracer.traceCompletable(tracer.spanBuilder("Execute 2")));

    Completable.concatArray(operation1, operation2)
        .compose(RxTracer.traceCompletable(tracer.spanBuilder("Subscribe")))
        .subscribe();
    // TODO: Add Jaeger trace screenshot
    // This will trace as:
    // - Subscribe
    // -   Execute 1
    // -   Execute 2
  }
}
```

## Scheduler Support
The default TracingAssembly support does not handle propagation through
schedulers. This library adds an additiona RxJava plugin to ensure the context
can propagate to another scheduler.

Given the following example:
```Java
  void longRunningOperation() {
    Span execute = tracer.spanBuilder("LongRunningOperation").startSpan();
    try (var s = execute.makeCurrent()) {
      LOG.info("Starting my long running operation");
      // ...
      LOG.info("Finished my long running operation");
    } finally {
      execute.end();
    }
  }
  
  void doStuff() {
    final var span = tracer.spanBuilder("Subscribe");

    Completable.fromRunnable(this::longRunningOperation)
            .subscribeOn(Schedulers.io())
            .compose(RxTracer.traceCompletable(span))
            .subscribe();
  }
```

The default tracing will result in two spans, 'Subscribe' and
'LongRunningOperation'. However they both will not have a parent.

The RxTracer can be configured to propagate the context across schedulers as
well so the 'LongRunningOperation' Span will have the 'Subscribe' span as the
parent.

```Java
RxTracingAssembly.builder().setEnableSchedulerPropagation(true).build().enable();
```

### Without Scheduler supprt
TODO: Add Jaeger pic
### With Scheduler support
TODO: Add Jaeger pic



## Trace Completable

```java
import io.github.ikstewa.opentelemetry.rxjava3.RxTracer;

// Via compose
Completable tracedCompletable = completable.compose(RxTracer.traceCompletable(SpanBuilder))

// Via static methods
Completable tracedCompletable = RxTracer.traceCompletable(Completable, SpanBuilder)
```

## Trace Single

```java
import io.github.ikstewa.opentelemetry.rxjava3.RxTracer;

// Via compose
Single tracedSingle = single.compose(RxTracer.traceSingle(SpanBuilder))

// Via static methods
Single tracedSingle = RxTracer.traceSingle(Maybe, SpanBuilder)
```


## Trace Maybe

```java
import io.github.ikstewa.opentelemetry.rxjava3.RxTracer;

// Via compose
Maybe tracedMaybe = maybe.compose(RxTracer.traceMaybe(SpanBuilder))

// Via static methods
Maybe tracedMaybe = RxTracer.traceMaybe(Maybe, SpanBuilder)
```

## Trace Observable

```java
import io.github.ikstewa.opentelemetry.rxjava3.RxTracer;

// Via compose
Observable tracedObservable = observable.compose(RxTracer.traceObservable(SpanBuilder))

// Via static methods
Observable tracedObservable = RxTracer.traceObservable(Observable, SpanBuilder)
```
## Trace Flowable

```java
import io.github.ikstewa.opentelemetry.rxjava3.RxTracer;

// Via compose
Flowable tracedFlowable = flowable.compose(RxTracer.traceFlowable(SpanBuilder))

// Via static methods
Flowable tracedFlowable = RxTracer.traceFlowable(Flowable, SpanBuilder)
```
