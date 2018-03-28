/*
 * Copyright 2018, OpenCensus Authors
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

package io.opencensus.contrib.http;

import static com.google.common.base.Preconditions.checkNotNull;

import io.opencensus.trace.Span;
import io.opencensus.trace.SpanBuilder;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.propagation.TextFormat;

/**
 * This helper class provides routine methods to instrument HTTP clients.
 *
 * @param <Q> the HTTP request entity.
 * @param <P> the HTTP response entity.
 * @since 0.13
 */
public final class HttpClientHandler<Q, P> extends HttpHandler<Q, P> {

  /**
   * Creates a {@link HttpClientHandler} with given parameters.
   *
   * @param tracer the Open Census tracing component.
   * @param extractor the {@code HttpExtractor} used to extract information from the
   *     request/response.
   * @param customizer the {@link HttpSpanCustomizer} used to customize span behaviors.
   * @since 0.13
   */
  public HttpClientHandler(
      Tracer tracer, HttpExtractor<Q, P> extractor, HttpSpanCustomizer<Q, P> customizer) {
    super(tracer, extractor, customizer);
  }

  /**
   * Instrument a request before it is sent. Users should optionally invoke {@link
   * #handleMessageSent} after the request is sent.
   *
   * <p>This method will create a span in current context to represent the HTTP call. The created
   * span will be serialized and propagated to the server.
   *
   * <p>This method will invoke {@link HttpSpanCustomizer#customizeSpanStart} after the span is
   * successfully created.
   *
   * <p>The generated span will NOT be set as current context. User can use the returned value to
   * control when to enter the scope of this span.
   *
   * @param <C> the type of the carrier.
   * @param textFormat the {@code TextFormat} used in HTTP propagation.
   * @param setter the setter used when injecting information to the {@code carrier}.
   * @param carrier the entity that holds the HTTP information.
   * @param request the request entity.
   * @return a span that represents the request process.
   * @since 0.13
   */
  public <C> Span handleStart(
      TextFormat textFormat, TextFormat.Setter<C> setter, C carrier, Q request) {
    checkNotNull(textFormat, "textFormat");
    checkNotNull(setter, "setter");
    checkNotNull(carrier, "carrier");
    checkNotNull(request, "request");
    String spanName = customizer.getSpanName(request, extractor);
    SpanBuilder builder =
        customizer.customizeSpanBuilder(request, tracer.spanBuilder(spanName), extractor);
    Span span = builder.startSpan();

    // user-defined behaviors
    customizer.customizeSpanStart(request, span, extractor);

    // inject propagation header
    SpanContext spanContext = span.getContext();
    if (!spanContext.equals(SpanContext.INVALID)) {
      textFormat.inject(spanContext, carrier, setter);
    }
    return span;
  }
}
