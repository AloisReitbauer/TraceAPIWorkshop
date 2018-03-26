package com.tracing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.dynatrace.oneagent.sdk.OneAgentSDKFactory;
import com.dynatrace.oneagent.sdk.api.DatabaseRequestTracer;
import com.dynatrace.oneagent.sdk.api.IncomingWebRequestTracer;
import com.dynatrace.oneagent.sdk.api.LoggingCallback;
import com.dynatrace.oneagent.sdk.api.OneAgentSDK;
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
					try {
						// call the server in a loop
						System.out.println("Client is calling");
						pos = (pos + 1) % 2;
						URL url = new URL("http://localhost:8000/" + pathList[pos]);
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");
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
		WebApplicationInfo webApplicationInfo = oneAgentSdk.createWebApplicationInfo("PathAHandler", "", "/");

		@Override
		public void handle(HttpExchange t) throws IOException {
			// traces an incoming request with dynatrace sdk
			// trace-context is read automatically
			IncomingWebRequestTracer tracer = oneAgentSdk.traceIncomingWebRequest(webApplicationInfo, t.getRequestURI().toString(), "GET");

			// record header properties of request (optional)
			for (String key : t.getRequestHeaders().keySet()) {
				for(String value : t.getRequestHeaders().get(key)) {
					tracer.addRequestHeader(key, value);
				}
			}

			tracer.start();
			try {
				String response = "This is path A";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path A was called.");
				os.close();
			} catch (Throwable e) {
				tracer.error(e.getMessage());
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

			tracer.start();
			try {
				String response = "This is path B";
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				System.out.println("Path B was called");
				this.fakeDBCall("select * from table");
				os.close();
			} catch (Throwable e) {
				tracer.error(e.getMessage());
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
			} catch (Throwable t) {
				tracer.error(t.getMessage());
			} finally {
				tracer.end();
			}
		}
	}
}
