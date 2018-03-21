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
		@Override
		public void handle(HttpExchange t) throws IOException {
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
