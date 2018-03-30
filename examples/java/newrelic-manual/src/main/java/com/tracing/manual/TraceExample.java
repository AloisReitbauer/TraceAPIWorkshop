package com.tracing.manual;

import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.agent.bridge.Transaction;
import com.newrelic.api.agent.DatastoreParameters;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Segment;
import com.newrelic.api.agent.Trace;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;


/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code
 * 
 * @author alois.reitbauer
 *
 */
public class TraceExample {

	public static void main(String[] args) {

		try {
			initServer();
			initClient();
		} catch (Exception ex) {
			System.err.println("Error starting example");
			System.err.println(ex.toString());
		}

	}

	private static void initClient() {

		Thread thread = new Thread(new Runnable() {
			int pos = 0;
			String[] pathList = { "pathA", "pathB" };

			@Override
			public void run() {
				for (;;) {
					callHandler();
				}
			}

			@Trace(dispatcher=true) // This annotation instruments the client
			private void callHandler() {
				try {
					// call the server in a loop
					System.out.println("Client is calling");
					pos = (pos + 1) % 2;
					URL url = new URL("http://localhost:8000/" + pathList[pos]);
					String payload =  AgentBridge.getAgent().getTransaction().createDistributedTracePayload().text();

					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("GET");
					con.setRequestProperty("NewRelicPayload", payload);
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
					return;
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
	private static void readNewRelicPayload(HttpExchange t, Transaction txn) {
		java.util.List<String> tags = t.getRequestHeaders().get("NewRelicPayload");
		if (tags.size() == 1) {
			txn.acceptDistributedTracePayload(tags.get(0));
		} else {
			System.err.println("zero or more than one tags");
		}
	}

	static class PathAHandler implements HttpHandler {
		@Override
		@Trace(dispatcher=true)
		public void handle(HttpExchange t) throws IOException {
			// add trace context to the Transaction
			readNewRelicPayload(t, AgentBridge.getAgent().getTransaction());

			String response = "This is path A";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			System.out.println("Path A was called.");
			os.close();
		}
	}

	static class PathBHandler implements HttpHandler {
		@Override
		@Trace(dispatcher=true)
		public void handle(HttpExchange t) throws IOException {
			// add trace context to the Transaction
			readNewRelicPayload(t, AgentBridge.getAgent().getTransaction());

			String response = "This is path B";
			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			System.out.println("Path B was called");
			this.fakeDBCall("select * from table");
			os.close();
		}

		public void fakeDBCall(String statement) {
			// add a segment to the Transaction
			Segment segment = NewRelic.getAgent().getTransaction().startSegment("database");
			segment.reportAsExternal(DatastoreParameters
					.product("FakeDB")
					.collection("tablename")
					.operation("SELECT")
					.build());
			// this is just to simulate a fake database call
			System.out.println("Fake DB! was called with statement " + statement);
			segment.end();
		}

	}
}
