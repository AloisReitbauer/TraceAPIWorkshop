package com.tracing;

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
import zipkin2.reporter.Reporter;



/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code
 * 
 * @author alois.reitbauer
 *
 */
public class TraceExample {

	public static void main(String[] args) throws InterruptedException, IOException {

		final Sender sender = URLConnectionSender.create("http://localhost:9411/api/v2/spans");
		final Reporter<zipkin2.Span> reporter = AsyncReporter.builder(sender).build();
		//final Reporter<zipkin2.Span> reporter = Reporter.CONSOLE;
		final Tracing tracing = Tracing
			.newBuilder()
			.localServiceName("rpc-testing")
			.spanReporter(reporter)
			.sampler(Sampler.ALWAYS_SAMPLE) /* or any other Sampler */
			
			.build();

		try {
			initServer(tracing);
			initClient(tracing);
		} catch (Exception ex) {
			System.err.println("Error starting example");
			System.err.println(ex.toString());
		}
	}

	private static void initClient(Tracing tracing) {
		Injector<HttpURLConnection> tracingInjector = tracing.propagation().injector(HttpURLConnection::addRequestProperty);

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
					try(SpanInScope scope = tracer.withSpanInScope(span)) {
						// add additional information
						span.tag("myrpc.version", "6.6.6");
						
						// call the server in a loop
						System.out.println("Client is calling");
						pos = (pos + 1) % 2;
						URL url = new URL("http://localhost:8000/" + pathList[pos]);
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");

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
					} catch (Exception e) {
						span.tag("error", e.getMessage()); // exception tagging

						System.err.println("Failed to talk to server");
						System.err.println(e.toString());
					} finally {
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
		Extractor<HttpExchange> tracingExtractor = tracing.propagation().extractor(new brave.propagation.Propagation.Getter<HttpExchange, String>() {
			@Override
			public String get(HttpExchange carrier, String key) {
				List<String> values = carrier.getRequestHeaders().get(key);
				if (values == null) return null;
				if (values.size() > 0) return values.get(0);
				return null;
			}
		});

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
			try (SpanInScope scope = tracer.withSpanInScope(span)) {
				// simulate some server time
				Thread.sleep(27);

				String response = "This is path A";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path A was called.");
				os.close();
				
				// simulate some more time, AFTER response has been sent
				Thread.sleep(7);
			} catch (Exception e) {
				span.tag("error", e.getMessage());
				System.err.println("Error in PathAHandler: " + e.getMessage());
			} finally {
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
			try (SpanInScope scope = tracer.withSpanInScope(span)) {

				String response = "This is path B";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path B was called");
				this.fakeDBCall("select * from table");
				os.close();

			} catch (Exception e) {
				span.tag("error", e.getMessage());
				System.err.println("Error in PathAHandler: " + e.getMessage());
			} finally {
				// close the span
				span.finish();
			}
		}

		public void fakeDBCall(String statement) {
			Span span = Tracing.currentTracer().nextSpan().name("database").start();
			try (SpanInScope scope = Tracing.currentTracer().withSpanInScope(span)) {
				// this is just to simulate a fake database call
				System.out.println("Fake DB was called with statement " + statement);
				Thread.sleep(100);
			} catch (InterruptedException e) {
				span.tag("error", e.getMessage());
				e.printStackTrace();
			} finally {
				span.finish();
			}
		}

	}
}
