/*
 * Copyright 2017, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opencensus.examples.trace;

import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.logging.LoggingExporter;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/** Example showing how to do context propagation in an automatical way. */
public final class AutomaticContextPropagation {
  // BEGIN: AutomaticContextPropagation#tracer
  // Per class Tracer.
  private static final Tracer tracer = Tracing.getTracer();
  // END: AutomaticContextPropagation#tracer

  static void doMoreWork() {
    tracer.getCurrentSpan().addAnnotation("DoMoreWork");
  }

  // BEGIN: AutomaticContextPropagation#doSomeWork
  static void doSomeWork() {
    // Create a Span as a child of current Span.
    // The created Span will NOT end after the scope is exited.
    // User has to manually end the span.
    Span span = tracer.spanBuilder("DoSomeWork").startSpan();
    try (Scope ws = tracer.withSpan(span)) {
      tracer.getCurrentSpan().addAnnotation("Annotation to the child Span");
      doMoreWork(); // Here "span" is the current Span.
    }
    span.end(); // Manually end the span.
  }
  // END: AutomaticContextPropagation#doSomeWork

  // BEGIN: AutomaticContextPropagation#doWork
  static void doWork() {
    // Create a Span as a child of current Span and set it as current scope.
    // The created Span will end once the scope is exited.
    try (Scope ss = tracer.spanBuilder("DoWork").startScopedSpan()) {
      tracer
          .getCurrentSpan()
          .addAnnotation("Annotation to the parent Span before child is created.");
      doSomeWork(); // Here the new span is in the current Context, so it can be used
      // implicitly anywhere down the stack. Anytime in this closure the span
      // can be accessed via tracer.getCurrentSpan().
      tracer.getCurrentSpan().addAnnotation("Annotation to the parent Span after child is ended.");
    }
  }
  // END: AutomaticContextPropagation#doWork

  // BEGIN: AutomaticContextPropagation#doSomeWorkPriorJava7
  // Demonstrate how to use Scope prior to Java SE 7 in which try-with-resources is not supported.
  static void doSomeWorkPriorJava7() {
    // Create a Span as a child of current Span.
    // The created Span will NOT end after the scope is exited.
    // User has to manually end the span.
    Span span = tracer.spanBuilder("DoSomeWork").startSpan();
    Scope ws = tracer.withSpan(span);
    try {
      tracer.getCurrentSpan().addAnnotation("Annotation to the child Span");
      doMoreWork(); // Here "span" is the current Span.
    } finally {
      ws.close();
    }
    span.end(); // Manually end the span.
  }
  // END: AutomaticContextPropagation#doSomeWorkPriorJava7

  // BEGIN: AutomaticContextPropagation#doWorkPriorJava7
  // Demonstrate how to use Scope prior to Java SE 7 in which try-with-resources is not supported.
  static void doWorkPriorJava7() {
    // Create a Span as a child of current Span and set it as current scope.
    // The created Span will end once the scope is exited.
    Scope ss = tracer.spanBuilder("DoWork").startScopedSpan();
    try {
      tracer
          .getCurrentSpan()
          .addAnnotation("Annotation to the parent Span before child is created.");
      doSomeWork(); // Here the new span is in the current Context, so it can be used
      // implicitly anywhere down the stack. Anytime in this closure the span
      // can be accessed via tracer.getCurrentSpan().
      tracer.getCurrentSpan().addAnnotation("Annotation to the parent Span after child is ended.");
    } finally {
      ss.close();
    }
  }
  // END: AutomaticContextPropagation#doWorkPriorJava7

  /**
   * Main method.
   *
   * @param args the main arguments.
   */
  public static void main(String[] args) {
    LoggingExporter.register();
    doWork();
  }
}
