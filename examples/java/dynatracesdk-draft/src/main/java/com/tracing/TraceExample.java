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

import com.dynatrace.oneagent.sdk.OneAgentSDKFactory;
import com.dynatrace.oneagent.sdk.api.DatabaseRequestTracer;
import com.dynatrace.oneagent.sdk.api.IncomingWebRequestTracer;
import com.dynatrace.oneagent.sdk.api.LoggingCallback;
import com.dynatrace.oneagent.sdk.api.OneAgentSDK;
import com.dynatrace.oneagent.sdk.api.OutgoingWebRequestTracer;
import com.dynatrace.oneagent.sdk.api.enums.ChannelType;
import com.dynatrace.oneagent.sdk.api.enums.DatabaseVendor;
import com.dynatrace.oneagent.sdk.api.infos.DatabaseInfo;
import com.dynatrace.oneagent.sdk.api.infos.WebApplicationInfo;

/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code
 * 
 * @author alois.reitbauer
 *
 */
public class TraceExample {
	// Dynatrace SDK will only work if a Dynatrace OneAgent is installed and injected
	// It will interact with auto-instrumentation provided by OneAgent
	// If OneAgent is not present, OneAgentSDK will be inactive.
	private static OneAgentSDK oneAgentSdk;

	public static void main(String[] args) {
		oneAgentSdk = OneAgentSDKFactory.createInstance();
		logSdkStatus();
		enableSdkLogging();

		try {
			initServer();
			initClient();
		} catch (Exception ex) {
			System.err.println("Error starting example");
			System.err.println(ex.toString());
		}
	}

	private static void enableSdkLogging() {
		oneAgentSdk.setLoggingCallback(new ConsoleLoggingCallback());
	}

	public static class ConsoleLoggingCallback implements LoggingCallback {
		@Override
		public void warn(String message) {
			System.out.println(message);
		}
	
		@Override
		public void error(String message) {
			System.out.println(message);
		}
	}

	private static void logSdkStatus() {
		switch (oneAgentSdk.getCurrentState()) {
		case ACTIVE:
			System.out.println("dynatrace sdk ACTIVE");
			break;
		case PERMANENTLY_INACTIVE:
			System.out.println("dynatrace sdk PERMANENTLY_INACTIVE");
			break;
		case TEMPORARILY_INACTIVE:
			System.out.println("dynatrace sdk TEMPORARILY_INACTIVE");
			break;
		default:
			System.out.println("dynatrace sdk status unknown");
			break;
		}
	}

	private static void initClient() {
		Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				int pos = 0;
				String[] pathList = { "pathA", "pathB" };
				for (;;) {
					// call the server in a loop
					System.out.println("Client is calling");
					pos = (pos + 1) % 2;
					String url = "http://localhost:8000/" + pathList[pos];
					
					OutgoingWebRequestTracer tracer = oneAgentSdk.traceOutgoingWebRequest(url, "GET");

					tracer.start();
					try {
						HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
						con.setRequestMethod("GET");

						// add dynatrace tag (trace context) to http header
						con.addRequestProperty(OneAgentSDK.DYNATRACE_HTTP_HEADERNAME, tracer.getDynatraceStringTag());

						tracer.setStatusCode(con.getResponseCode());
						
						BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
						String inputLine;
						StringBuffer content = new StringBuffer();
						while ((inputLine = in.readLine()) != null) {
							content.append(inputLine);
						}
						in.close();
					} catch (Exception e) {
						tracer.error(e);
						
						System.err.println("Failed to talk to server");
						e.printStackTrace();
					} finally {
						tracer.end();
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

	private static String getHeader(HttpExchange t, String key) {
		List<String> values = t.getRequestHeaders().get(key);
		if (values == null || values.isEmpty()) return null;
		return values.get(0);
	}

	@SuppressWarnings("unused")
	private static void extractAllHeaders(HttpExchange t, IncomingWebRequestTracer tracer) {
		// read headers, if dynatrace-tracetag is found, tracing happens automatically
		for (String key : t.getRequestHeaders().keySet()) {
			for(String value : t.getRequestHeaders().get(key)) {
				tracer.addRequestHeader(key, value);
			}
		}
	}

	// server side handlers

	static class PathAHandler implements HttpHandler {
		WebApplicationInfo webApplicationInfo = oneAgentSdk.createWebApplicationInfo("PathAHandler", "", "/");

		@Override
		public void handle(HttpExchange t) throws IOException {
			// traces an incoming request with dynatrace sdk
			// trace-context is read automatically
			IncomingWebRequestTracer tracer = oneAgentSdk.traceIncomingWebRequest(webApplicationInfo, t.getRequestURI().toString(), "GET");

			// trace explicitly
			tracer.setDynatraceStringTag(getHeader(t, OneAgentSDK.DYNATRACE_HTTP_HEADERNAME));

			// to be compatible with dynatrace default sensors, just extract all headers and tracing will happen automatically:
			// extractAllHeaders(t, tracer);

			tracer.start();
			try {
				String response = "This is path A";
				t.sendResponseHeaders(200, response.length());
				tracer.setStatusCode(200);

				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path A was called.");
				os.close();
			} catch (Exception e) {
				tracer.error(e);
				e.printStackTrace();
			} finally {
				tracer.end();
			}
		}
	}

	static class PathBHandler implements HttpHandler {
		WebApplicationInfo webApplicationInfo = oneAgentSdk.createWebApplicationInfo("PathAHandler", "", "/");
		DatabaseInfo databaseInfo = oneAgentSdk.createDatabaseInfo("mySampleDb", DatabaseVendor.POSTGRESQL.toString(), ChannelType.TCP_IP, "localhost");

		@Override
		public void handle(HttpExchange t) throws IOException {
			IncomingWebRequestTracer tracer = oneAgentSdk.traceIncomingWebRequest(webApplicationInfo, t.getRequestURI().toString(), "GET");

			// trace explicitly
			tracer.setDynatraceStringTag(getHeader(t, OneAgentSDK.DYNATRACE_HTTP_HEADERNAME));

			// to be compatible with dynatrace default sensors, just extract all headers and tracing will happen automatically:
			// extractAllHeaders(t, tracer);

			tracer.start();
			try {
				String response = "This is path B";
				t.sendResponseHeaders(200, response.length());
				tracer.setStatusCode(200);

				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path B was called");
				this.fakeDBCall("select * from table");
				os.close();
			} catch (Exception e) {
				tracer.error(e);
				e.printStackTrace();
			} finally {
				tracer.end();
			}
		}

		public void fakeDBCall(String statement) {
			DatabaseRequestTracer tracer = oneAgentSdk.traceSQLDatabaseRequest(databaseInfo, statement);

			tracer.start();
			try {
				// this is just to simulate a fake database call
				System.out.println("Fake DB was called with statement " + statement);
			} catch (Exception e) {
				tracer.error(e);
				e.printStackTrace();
			} finally {
				tracer.end();
			}
		}
	}
}
