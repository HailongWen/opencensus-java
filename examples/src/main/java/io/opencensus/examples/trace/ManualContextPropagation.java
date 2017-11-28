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

import io.opencensus.exporter.trace.logging.LoggingExporter;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/** Example showing how to do context propagation in a manual way. */
public final class ManualContextPropagation {
  // BEGIN: ManualContextPropagation#tracer
  // Per class Tracer.
  private static final Tracer tracer = Tracing.getTracer();
  // END: ManualContextPropagation#tracer

  static void doMoreWork(Span child) {
    child.addAnnotation("DoMoreWork");
  }

  // BEGIN: ManualContextPropagation#doSomeWork
  static void doSomeWork(Span parent) {
    Span child = tracer.spanBuilderWithExplicitParent("DoSomeWork", parent).startSpan();
    try {
      child.addAnnotation("Annotation to the child Span");
      doMoreWork(child); // Manually propagate the span down the stack.
    } finally {
      // To make sure we end the span even in case of an exception.
      child.end(); // Manually end the span.
    }
  }
  // END: ManualContextPropagation#doSomeWork

  // BEGIN: ManualContextPropagation#doWork
  static void doWork() {
    Span parent = tracer.spanBuilder("DoWork").startSpan();
    try {
      parent.addAnnotation("Annotation to the parent Span before child is created.");
      doSomeWork(parent); // Manually propagate the span down the stack.
      parent.addAnnotation("Annotation to the parent Span after child is ended.");
    } finally {
      // To make sure we end the span even in case of an exception.
      parent.end(); // Manually end the span.
    }
  }
  // END: ManualContextPropagation#doWork

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
