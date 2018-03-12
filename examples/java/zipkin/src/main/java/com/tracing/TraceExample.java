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
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.TraceContext.Extractor;
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
					// add additional information
					span.tag("myrpc.version", "6.6.6");
				
					try {
						// call the server in a loop
						System.out.println("Client is calling");
						pos = (pos + 1) % 2;
						URL url = new URL("http://localhost:8000/" + pathList[pos]);
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");

						// add the trace-context
						tracing.propagation().injector(HttpURLConnection::addRequestProperty)
							.inject(span.context(), con);
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
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/pathA", new PathAHandler(tracing));
		server.createContext("/pathB", new PathBHandler());
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	// server side handlers

	static class PathAHandler implements HttpHandler {
		private final Tracing tracing;

		public PathAHandler(Tracing tracing) {
			super();
			this.tracing = tracing;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			try {
				// this extractor is needed, since zipkin does not support HttpExchange directly
				Extractor<HttpExchange> extractor = tracing.propagation().extractor(new brave.propagation.Propagation.Getter<HttpExchange, String>() {
					@Override
					public String get(HttpExchange carrier, String key) {
						List<String> values = carrier.getRequestHeaders().get(key);
						if (values == null) return null;
						if (values.size() > 0) return values.get(0);
						return null;
					}
				});

				// read incoming context
				TraceContextOrSamplingFlags incomingContext = extractor.extract(t);
				Tracer tracer = tracing.tracer();
				// new span with parent info from incoming context
				Span span = tracer.nextSpan(incomingContext).kind(Kind.SERVER).name("PathAHandler").start();
				try {
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
				} finally {
					// close the span
					span.finish();
				}
			} catch (Exception e) {
				System.err.println("Error in PathAHandler: " + e.getMessage());
			} finally {
			}
		}
	}

	static class PathBHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String response = "This is path B";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			System.out.println("Path B was called");
			this.fakeDBCall("select * from table");
			os.close();
		}

		public void fakeDBCall(String statement) {

			// this is just to simulate a fake database call
			System.out.println("Fake DB was called with statement " + statement);
		}

	}
}
