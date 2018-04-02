package com.tracing;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.Span.Kind;
import brave.Tracer.SpanInScope;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.sampler.Sampler;
import zipkin2.reporter.Sender;
import zipkin2.reporter.urlconnection.URLConnectionSender;
import zipkin2.reporter.AsyncReporter;



/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code
 * 
 * @author alois.reitbauer
 *
 */
public class TraceExample {

	/** Brave's propagation library builds library-specific injectors and extractors */
	static final Getter<HttpExchange, String> GETTER = new Getter<HttpExchange, String>() {
		@Override public String get(HttpExchange carrier, String key) {
			List<String> values = carrier.getRequestHeaders().get(key);
			if (values == null) return null;
			if (values.size() > 0) return values.get(0);
			return null;
		}
	};
	static final Setter<HttpURLConnection, String> SETTER = new Setter<HttpURLConnection, String>() {
		@Override public void put(HttpURLConnection httpURLConnection, String key, String value) {
			httpURLConnection.addRequestProperty(key, value);
		}
	};

	public static void main(String[] args) {

		final Sender sender = URLConnectionSender.create("http://localhost:9411/api/v2/spans");
		final AsyncReporter<zipkin2.Span> reporter = AsyncReporter.builder(sender).build();
		//final Reporter<zipkin2.Span> reporter = Reporter.CONSOLE;
		final Tracing tracing = Tracing
			.newBuilder()
			.localServiceName("rpc-testing")
			.spanReporter(reporter)
			.sampler(Sampler.ALWAYS_SAMPLE) /* or any other Sampler */
			.build();

		// Make sure spans get reported on control-c
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override public void run() {
				reporter.close();
			}
		});

		try {
			initServer(tracing);
			initClient(tracing);
		} catch (Exception ex) {
			System.err.println("Error starting example");
			System.err.println(ex.toString());
		}
	}

	private static void initClient(final Tracing tracing) {
		final Injector<HttpURLConnection> tracingInjector = tracing.propagation().injector(SETTER);

		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {

				int pos = 0;
				String[] pathList = { "pathA", "pathB" };
				for (;;) {
					// start trace
					Tracer tracer = tracing.tracer();
					// start root span
					Span span = tracer.nextSpan().name("client").kind(Kind.CLIENT);
					SpanInScope scope = tracer.withSpanInScope(span);
					try {
						// add additional information
						
						// call the server in a loop
						System.out.println("Client is calling");
						pos = (pos + 1) % 2;
						URL url = new URL("http://localhost:8000/" + pathList[pos]);
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");
						
						span.tag("http.url", url.toString());
						span.tag("http.method", "GET");
						// add the trace-context
						
						tracingInjector.inject(span.context(), con);
						span.start();
						
						con.addRequestProperty("testheader", "testvalue");
						
						con.connect();
						
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						String inputLine;
						StringBuffer content = new StringBuffer();
						while ((inputLine = in.readLine()) != null) {
							content.append(inputLine);
						}
						in.close();
						span.tag("http.status_code", Integer.toString(con.getResponseCode()));
					} catch (Exception e) {
						span.tag("error", e.getMessage()); // exception tagging

						System.err.println("Failed to talk to server");
						System.err.println(e.toString());
					} finally {
						scope.close();
						span.finish();
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

	private static void initServer(Tracing tracing) throws Exception {
		// this extractor is needed, since zipkin does not support HttpExchange directly
		Extractor<HttpExchange> tracingExtractor = tracing.propagation().extractor(GETTER);

		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/pathA", new PathAHandler(tracing, tracingExtractor));
		server.createContext("/pathB", new PathBHandler(tracing, tracingExtractor));
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	// server side handlers

	static class PathAHandler implements HttpHandler {
		private final Tracing tracing;
		private final Extractor<HttpExchange> tracingExtractor;

		public PathAHandler(Tracing tracing, Extractor<HttpExchange> tracingExtractor) {
			super();
			this.tracing = tracing;
			 this.tracingExtractor = tracingExtractor;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			// read incoming context
			TraceContextOrSamplingFlags incomingContext = tracingExtractor.extract(t);
			Tracer tracer = tracing.tracer();
			// new span with parent info from incoming context
			Span span = tracer.nextSpan(incomingContext).kind(Kind.SERVER).name("PathAHandler").start();
			SpanInScope scope = tracer.withSpanInScope(span);
			try {
				// simulate some server time
				Thread.sleep(27);

				String response = "This is path A";
				t.sendResponseHeaders(200, response.length());
				span.tag("http.url", t.getRequestURI().toString());
				span.tag("http.method", t.getRequestMethod());
				span.tag("http.status_code", "200");

				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path A was called.");
				os.close();
				
				// simulate some more time, AFTER response has been sent
				Thread.sleep(7);
			} catch (Exception e) {
				span.tag("error", e.getMessage());
				e.printStackTrace();
			} finally {
				scope.close();
				// close the span
				span.finish();
			}
		}
	}

	static class PathBHandler implements HttpHandler {
		private final Tracing tracing;
		private final Extractor<HttpExchange> tracingExtractor;

		public PathBHandler(Tracing tracing, Extractor<HttpExchange> tracingExtractor) {
			super();
			this.tracing = tracing;
			 this.tracingExtractor = tracingExtractor;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			// read incoming context
			TraceContextOrSamplingFlags incomingContext = tracingExtractor.extract(t);
			Tracer tracer = tracing.tracer();
			// new span with parent info from incoming context
			Span span = tracer.nextSpan(incomingContext).kind(Kind.SERVER).name("PathBHandler").start();
			SpanInScope scope = tracer.withSpanInScope(span);
			try {

				String response = "This is path B";
				t.sendResponseHeaders(200, response.length());
				span.tag("http.url", t.getRequestURI().toString());
				span.tag("http.method", t.getRequestMethod());
				span.tag("http.status_code", "200");

				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path B was called");
				this.fakeDBCall("select * from table");
				os.close();

			} catch (Exception e) {
				span.tag("error", e.getMessage());
				e.printStackTrace();
			} finally {
				scope.close();
				// close the span
				span.finish();
			}
		}

		public void fakeDBCall(String statement) {
			Span span = Tracing.currentTracer().nextSpan().name("database").start();
			SpanInScope scope = Tracing.currentTracer().withSpanInScope(span);
			try {
				span.tag("db.instance", "mySampleDb");
				span.tag("db.type", "sql");
				span.tag("db.statement", statement);
				
				// this is just to simulate a fake database call
				System.out.println("Fake DB was called with statement " + statement);
				Thread.sleep(100);
			} catch (InterruptedException e) {
				span.tag("error", e.getMessage());
				e.printStackTrace();
			} finally {
				scope.close();
				span.finish();
			}
		}
	}
}
