package com.tracing;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.RequestWithHeaders;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import static com.nike.wingtips.http.HttpRequestTracingUtils.fromRequestWithHeaders;
import static com.nike.wingtips.http.HttpRequestTracingUtils.propagateTracingHeaders;

/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code
 *
 * @author alois.reitbauer
 * @author Nic Munroe
 *
 */
public class WingtipsTraceExampleBasic {

	private static final Logger classLogger = LoggerFactory.getLogger(WingtipsTraceExampleBasic.class);
	private static final Logger clientLogger = LoggerFactory.getLogger("CLIENT_LOGGER");
	private static final Logger serverLogger = LoggerFactory.getLogger("SERVER_LOGGER");

	private static final Tracer wingtipsTracer = Tracer.getInstance();

	public static void main(String[] args) {

		try {
			classLogger.info("WINGTIPS EXAMPLE - BASIC");
			initServer();
			initClient();
		} catch (Exception ex) {
			classLogger.error("Error starting example", ex);
		}

	}

	private static void initClient() {

		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {

				int pos = 0;
				String[] pathList = { "pathA", "pathB" };
				for (;;) {
					try {
						// call the server in a loop
						pos = (pos + 1) % 2;
						String pathToCall = pathList[pos];
						clientLogger.info("\n\n=============");

						// Start the client trace
						wingtipsTracer.startSpanInCurrentContext("clientCalling-GET_/" + pathToCall, SpanPurpose.CLIENT);

						// Setup the HTTP client call
						clientLogger.info("Client is calling: /" + pathToCall);
						URL url = new URL("http://localhost:8000/" + pathToCall);
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");

						// Propagate the current span's tracing state on the HTTP client call's headers (Zipkin/B3 format)
						propagateTracingHeaders(con::setRequestProperty, wingtipsTracer.getCurrentSpan());

						// Execute the HTTP client call
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						String inputLine;
						StringBuffer content = new StringBuffer();
						while ((inputLine = in.readLine()) != null) {
							content.append(inputLine);
						}
						clientLogger.info("Received result from server: {}", content);
						in.close();
					} catch (Exception e) {
						clientLogger.error("Failed to talk to server", e);
					} finally {
						// Complete the client request span
						//      (which effectively completes the whole trace since there is no parent)
						wingtipsTracer.completeRequestSpan();
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

	private static void initServer() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/pathA", new PathAHandler());
		server.createContext("/pathB", new PathBHandler());
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	// server side handlers

	static class PathAHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			try {
				// Start an overall request span based on the incoming request.
				//      If the request has tracing headers then create a child span, otherwise start a root span.
				startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(t);
				serverLogger.info("Path A was called");

				String response = "This is path A";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
			}
			finally {
				// Complete the overall request span.
				wingtipsTracer.completeRequestSpan();
			}
		}
	}

	static class PathBHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			try {
				// Start an overall request span based on the incoming request.
				//      If the request has tracing headers then create a child span, otherwise start a root span.
				startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(t);
				serverLogger.info("Path B was called");

				this.fakeDBCall("select * from table");

				String response = "This is path B";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
			}
			finally {
				// Complete the overall request span.
				wingtipsTracer.completeRequestSpan();
			}
		}

		public void fakeDBCall(String statement) {

			// this is just to simulate a fake database call
			try {
				// Start a subspan (child span) for the database call. This way we get timing info for the database
				//      call even though the database itself doesn't support tracing.
				wingtipsTracer.startSubSpan("fakeDatabaseCall", SpanPurpose.CLIENT);

				// Execute the database call
				serverLogger.info("Fake DB was called with statement " + statement);
			}
			finally {
				// Complete the subspan around the database call.
				wingtipsTracer.completeSubSpan();
			}
		}

	}

	static void startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(HttpExchange t) {
		Span parentSpan = fromRequestWithHeaders(new RequestWithHeadersForHttpExchange(t), null);
		String spanName = "serverHandling-" + t.getRequestMethod() + "_" + t.getRequestURI().getPath();
		if (parentSpan == null) {
			wingtipsTracer.startRequestWithRootSpan(spanName);
		}
		else {
			wingtipsTracer.startRequestWithChildSpan(parentSpan, spanName);
		}
	}

	static class RequestWithHeadersForHttpExchange implements RequestWithHeaders {

		private final HttpExchange request;

		RequestWithHeadersForHttpExchange(HttpExchange request) {
			this.request = request;
		}

		@Override
		public String getHeader(String headerName) {
			return request.getRequestHeaders().getFirst(headerName);
		}

		@Override
		public Object getAttribute(String name) {
			return request.getAttribute(name);
		}
	}
}
