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

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import io.opencensus.trace.Annotation;
import io.opencensus.trace.NetworkEvent;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.export.SampledSpanStore;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.export.SpanData.TimedEvent;
import io.opencensus.trace.export.SpanExporter.Handler;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for google-http-java-client and opencensus-java. */
@RunWith(JUnit4.class)
public class GoogleHttpJavaClientInteropTesting {

  private HttpTransport transport = new NetHttpTransport();
  private HttpRequestFactory factory = transport.createRequestFactory(null);
  private HttpRequest request = null;
  private HttpResponse response = null;
  private SampledSpanStore sampledSpanStore = Tracing.getExportComponent().getSampledSpanStore();
  private TraceParams sampledTraceParams =
        TraceParams.DEFAULT.toBuilder().setSampler(Samplers.alwaysSample()).build();
  private List<SpanData> exportedSpans;
  private Comparator<TimedEvent> timedEventComparator = (TimedEvent a, TimedEvent b) -> {
    return a.getTimestamp().compareTo(b.getTimestamp());
  };

  @Before
  public void setUp() {
    // reset to default.
    Tracing.getTraceConfig().updateActiveTraceParams(TraceParams.DEFAULT);
    Tracing.getExportComponent().getSpanExporter().registerHandler("export", new Handler() {
      @Override
      public void export(Collection<SpanData> spanDataList) {
        for (SpanData spanData : spanDataList) {
          exportedSpans.add(spanData);
        }
      }
    });
    exportedSpans = new ArrayList<SpanData>();
  }

  @After
  public void cleanUp() {
    Tracing.getExportComponent().getSpanExporter().unregisterHandler("export");
  }

  /**
   * Make a request with given method, url and content.
   */
  void doHttpRequest(String method, String url, final String content) {
    try {
      HttpContent httpContent = content == null ? null : new HttpContent() {
        @Override
        public long getLength() throws IOException {
          return content.length();
        }

        @Override
        public String getType() {
          return "plain/text";
        }

        @Override
        public boolean retrySupported() {
          return true;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
          out.write(content.getBytes());
        }
      };
      request = factory.buildRequest(method, new GenericUrl(url), httpContent);
      response = request.execute();
    } catch (Exception e) {
      // nothing.
    }
  }

  void waitForExport() {
    try {
      Thread.sleep(5000);
    } catch (Exception e) {
      // nothing
    }
  }

  String getHeaderValue() {
    return request.getHeaders().getFirstHeaderStringValue("X-Cloud-Trace-Context");
  }

  @Test
  public void headerShouldPresent() {
    doHttpRequest("GET", "http://www.google.com", null);
    // the header exists and by default not sampled.
    assertThat(getHeaderValue()).contains(";o=0");

    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);
    doHttpRequest("GET", "http://github.com", null);
    // this time it should be sampled
    assertThat(getHeaderValue()).contains(";o=1");
  }

  @Test
  public void spanShouldBeSampledToLocal() {
    doHttpRequest("GET", "http://www.google.com", null);
    // the span should be locally collected.
    assertThat(sampledSpanStore.getSummary().getPerSpanNameSummary()).containsKey(
        "Sent.com.google.api.client.http.HttpRequest.execute");
  }

  @Test
  public void spanShouldRecordAnnotationEventAndStatus() {
    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);

    doHttpRequest("GET", "https://stackoverflow.com/search?q=opencensus", null);
    waitForExport();

    for (SpanData spanData : exportedSpans) {
      // output for eyeball check
      System.out.println("spanShouldRecordAnnotationEventAndStatus: " + spanData);

      // at least two events, one sent and one recv (may be more if retry)
      assertThat(spanData.getNetworkEvents().getEvents().size() >= 2).isTrue();
      // at least one retry should exist.
      assertThat(spanData.getAnnotations().getEvents().size() > 0).isTrue();
      // status should be set
      assertThat(spanData.getStatus()).isEqualTo(Status.OK);
    }
  }

  @Test
  public void spanShouldHaveNonNegativeSentMessageSize() {
    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);

    // disallow anonymous POST search.
    // 403, but that does not matter - only test against sent message size.
    doHttpRequest("POST", "http://github.com/search", "q=opencensus");
    waitForExport();
    for (SpanData spanData : exportedSpans) {
      // output for eyeball check
      System.out.println("spanShouldHaveNonNegativeSentMessageSize: " + spanData);

      List<TimedEvent<NetworkEvent>> events = new ArrayList(spanData.getNetworkEvents().getEvents());
      Collections.sort(events, timedEventComparator);
      // last but two should be sent
      NetworkEvent sentEvent = events.get(events.size()-2).getEvent();
      assertThat(sentEvent.getType()).isEqualTo(NetworkEvent.Type.SENT);
      assertThat(sentEvent.getUncompressedMessageSize() > 0).isTrue();
    }
  }

  @Test
  public void spanShouldHaveNonNegativeReceivedMessageSize() {
    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);

    doHttpRequest("GET", "http://www.google.com", null);
    waitForExport();
    for (SpanData spanData : exportedSpans) {
      // output for eyeball check
      System.out.println("spanShouldHaveNonNegativeReceivedMessageSize: " + spanData);

      List<TimedEvent<NetworkEvent>> events = new ArrayList(spanData.getNetworkEvents().getEvents());
      Collections.sort(events, timedEventComparator);
      // last but one should be received
      NetworkEvent recvEvent = events.get(events.size()-1).getEvent();
      assertThat(recvEvent.getType()).isEqualTo(NetworkEvent.Type.RECV);
      assertThat(recvEvent.getUncompressedMessageSize() > 0).isTrue();
    }

  }

  @Test
  public void spanShouldHaveCorrectRetryAnnotation() {
    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);

    // disallow anonymous POST search.
    // 403, but that does not matter - only test against retry.
    doHttpRequest("POST", "http://github.com/search", "q=opencensus");
    waitForExport();
    for (SpanData spanData : exportedSpans) {
      // output for eyeball check
      System.out.println("spanShouldHaveCorrectRetryAnnotation: " + spanData);

      List<TimedEvent<Annotation>> annotations = new ArrayList(spanData.getAnnotations().getEvents());
      Collections.sort(annotations, timedEventComparator);

      // should have at least 2 retries.
      assertThat(annotations.size() > 1).isTrue();
      for (int i = 0; i < annotations.size(); ++i) {
        assertThat(annotations.get(i).getEvent().getDescription()).isEqualTo("retry #" + i);
      }
    }
  }

  @Test
  public void spanShouldSetStatusOnFailure() {
    Tracing.getTraceConfig().updateActiveTraceParams(sampledTraceParams);

    // disallow anonymous post, 403 forbidden (get should be OK)
    doHttpRequest("POST", "http://github.com/search", "q=opencensus");
    waitForExport();
    for (SpanData spanData : exportedSpans) {
      // output for eyeball check
      System.out.println("spanShouldSetStatusOnFailure: " + spanData);
      assertThat(spanData.getStatus()).isEqualTo(Status.PERMISSION_DENIED);
    }
  }
}
