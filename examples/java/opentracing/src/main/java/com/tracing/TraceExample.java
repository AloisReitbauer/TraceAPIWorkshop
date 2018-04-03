package com.tracing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.log.Fields;
import io.opentracing.mock.MockTracer;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The OpenTracing TraceExample is designed to illustrate idiomatic use of OpenTracing
 * instrumentation in the context of a simple, self-contained Java example.
 */
public class TraceExample {

    public static void main(String[] args) {

        // Dynamically load tracer. (Use of the TracerResolver utility is optional)
        Tracer tracer = TracerResolver.resolveTracer();
        if (tracer == null) {
            // Instantiate a simple tracer; in this case, MockTracer.
            tracer = new MockTracer();
        }

        try {
            initServer(tracer);
            initClient(tracer);
        } catch (Exception ex) {
            System.err.println("Error starting example");
            System.err.println(ex.toString());
        }

    }


    ///////////////////////////////////////////////////////////////////
    // CLIENT-SIDE CODE
    ///////////////////////////////////////////////////////////////////

    private static void initClient(Tracer tracer) {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {

                int pos = 0;
                String[] pathList = {"pathA", "pathB"};

                // Call the server in a loop.
                for (;;) {
                    pos = (pos + 1) % 2;

                    // Prepare a Span with appropriate tags for an HTTP client call.
                    Span span = tracer.buildSpan("client." + pathList[pos])
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .withTag(Tags.HTTP_METHOD.getKey(), "GET").start();
                    try (Scope ignore = tracer.scopeManager().activate(span, true)) {
                        System.out.println("Client is calling");

                        URL url = new URL("http://localhost:8000/" + pathList[pos]);

                        // Record the HTTP_URL as a Span tag.
                        span.setTag(Tags.HTTP_URL.getKey(), url.toString());

                        HttpURLConnection con = (HttpURLConnection) url.openConnection();
                        con.setRequestMethod("GET");

                        // Inject span context into http headers.
                        tracer.inject(span.context(), Builtin.HTTP_HEADERS, new TextMapInjectAdapter(con));

                        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        String inputLine;
                        StringBuffer content = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        in.close();
                    } catch (Exception e) {
                        System.err.println("Failed to talk to server");
                        System.err.println(e.toString());

                        // log errors to the span.
                        onError(e, span);
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }

        });
        thread.start();
    }

    /**
     * A helper to record an arbitrary Throwable as an event log for the provided Span.
     */
    private static void onError(Throwable throwable, Span span) {
        Tags.ERROR.set(span, Boolean.TRUE);

        if (throwable != null) {
            Map<String, Object> errorLogs = new HashMap<>(2);
            errorLogs.put(Fields.EVENT, Tags.ERROR.getKey());
            errorLogs.put(Fields.ERROR_OBJECT, throwable);
            span.log(errorLogs);
        }
    }


    ///////////////////////////////////////////////////////////////////
    // SERVER-SIDE CODE
    ///////////////////////////////////////////////////////////////////

    private static void initServer(Tracer tracer) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/pathA", new PathAHandler(tracer));
        server.createContext("/pathB", new PathBHandler(tracer));
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    static class PathAHandler implements HttpHandler {

        private final Tracer tracer;

        PathAHandler(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            SpanBuilder spanBuilder = tracer.buildSpan("server")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.HTTP_STATUS.getKey(), t.getResponseCode())
                // Note that .asChildOf() accepts (and ignores) null
                .asChildOf(tracer.extract(Builtin.HTTP_HEADERS, new TextMapExtractAdapter(t)));

            try (Scope ignore = tracer.scopeManager().activate(spanBuilder.start(), true)) {
                String response = "This is path A";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                System.out.println("Path A was called.");
                os.close();
            }
        }
    }

    static class PathBHandler implements HttpHandler {

        private final Tracer tracer;

        PathBHandler(Tracer tracer) {
            this.tracer = tracer;
        }


        @Override
        public void handle(HttpExchange t) throws IOException {
            SpanBuilder spanBuilder = tracer.buildSpan("server")
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.HTTP_STATUS.getKey(), t.getResponseCode())
                // Note that .asChildOf() accepts (and ignores) null
                .asChildOf(tracer.extract(Builtin.HTTP_HEADERS, new TextMapExtractAdapter(t)));

            try (Scope ignore = tracer.scopeManager().activate(spanBuilder.start(), true)) {
                String response = "This is path B";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                System.out.println("Path B was called");
                this.fakeDBCall("select * from table");
                os.close();
            }
        }

        void fakeDBCall(String statement) {
            try (Scope ignore = tracer.scopeManager().activate(
                tracer.buildSpan("database")
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(Tags.DB_STATEMENT.getKey(), statement)
                    .start(), true)) {
                // this is just to simulate a fake database call
                System.out.println("Fake DB was called with statement " + statement);
            }
        }
    }

    /**
     * Helper class to inject SpanContext into HTTP headers.
     *
     * In "the real world", this TextMap implementation would be a detail of whatever web framework
     * one depends on (e.g., DropWizard or similar).
     */
    static class TextMapInjectAdapter implements TextMap {

        private final HttpURLConnection con;

        TextMapInjectAdapter(HttpURLConnection con) {
            this.con = con;
        }

        @Override
        public Iterator<Entry<String, String>> iterator() {
            throw new UnsupportedOperationException("iterator should never be used with Tracer.inject()");
        }

        @Override
        public void put(String key, String value) {
            con.addRequestProperty(key, value);
        }
    }

    /**
     * Helper class to extract SpanContext from HTTP headers.
     *
     * In "the real world", this TextMap implementation would be a detail of whatever web framework
     * one depends on (e.g., DropWizard or similar).
     */
    static class TextMapExtractAdapter implements TextMap {

        private final Map<String, String> map = new HashMap<>();

        TextMapExtractAdapter(HttpExchange exchange) {
            for (Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                map.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
            }
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return map.entrySet().iterator();
        }

        @Override
        public void put(String key, String value) {
            throw new UnsupportedOperationException(
                "TextMapExtractAdapter should only be used with Tracer.extract()");
        }
    }
}
