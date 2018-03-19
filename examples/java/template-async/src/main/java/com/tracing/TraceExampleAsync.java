package com.tracing;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Single self contained example for a distributed micro application to
 * demonstrate how different tracing APIs instrument code.
 *
 * <p>This particular class demonstrates instrumenting asynchronous scenarios.
 *
 * @author alois.reitbauer
 * @author Nic Munroe
 *
 */
public class TraceExampleAsync {

	public static void main(String[] args) {

		try {
			initServer();
			initClient();
		} catch (Exception ex) {
			System.err.println("Error starting example");
			ex.printStackTrace();
		}

	}

	private static void initClient() {

		Thread thread = new Thread(() -> {

			int pos = 0;
			String[] pathList = { "asyncWithExecutor", "asyncWithCompletableFuture", "asyncWithCallback" };
			// call the server in a loop
			for (;;) {
				try {
					pos = (pos + 1) % pathList.length;
					String pathToCall = pathList[pos];
					System.out.println("\n=============");

					// Setup the HTTP client call
					System.out.println("Client is calling: /" + pathToCall);
					URL url = new URL("http://localhost:8000/" + pathToCall);
					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("GET");

					// Execute the HTTP client call
					BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
					String inputLine;
					StringBuilder content = new StringBuilder();
					while ((inputLine = in.readLine()) != null) {
						content.append(inputLine);
					}
					System.out.println("Received result from server: " + content);
					in.close();
				} catch (Exception e) {
					System.err.println("Failed to talk to server");
					e.printStackTrace();
				}

				sleepForMillis(1000);
			}
		});
		thread.start();
	}

	private static void initServer() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		server.createContext("/asyncWithExecutor", new AsyncWithExecutorPathHandler());
		server.createContext("/asyncWithCompletableFuture", new AsyncWithCompletableFuturePathHandler());
		server.createContext("/asyncWithCallback", new AsyncWithCallbackPathHandler());
		server.setExecutor(null); // creates a default executor
		server.start();
	}

	// server side handlers

	static class AsyncWithExecutorPathHandler implements HttpHandler {

		// This is an Executor you control. You can wrap it or replace it with a different Executor.
		private static final Executor requestProcessingExecutorYouControl = Executors.newCachedThreadPool();

		@Override
		public void handle(HttpExchange t) {
			System.out.println("/asyncWithExecutor path was called.");

			requestProcessingExecutorYouControl.execute(() -> {
				try {
					sleepForMillis(5);

					System.out.println("In async thread - about to respond to caller.");

					String response = "This is the asyncWithExecutor path";
					t.sendResponseHeaders(200, response.length());
					OutputStream os = t.getResponseBody();
					os.write(response.getBytes());
					os.close();
				}
				catch (IOException e) {
					System.err.println("An error occurred while processing the request.");
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			});
		}

	}

	static class AsyncWithCompletableFuturePathHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) {
			System.out.println("/asyncWithCompletableFuture path was called.");

			CompletableFuture.supplyAsync(
				() -> {
					sleepForMillis(5);

					String dataForResponse = "cf-data_" + UUID.randomUUID().toString();
					System.out.println("In async CompletableFuture, about to respond with data: " + dataForResponse);

					try {
						String response = "This is the asyncWithCompletableFuture path - "
										  + "data supplied by the CompletableFuture: " + dataForResponse;
						t.sendResponseHeaders(200, response.length());
						OutputStream os = t.getResponseBody();
						os.write(response.getBytes());
						os.close();
					}
					catch (IOException e) {
						System.err.println("An error occurred while processing the request.");
						e.printStackTrace();
						throw new RuntimeException(e);
					}

					return null;
				}
			);
		}

	}

	static class AsyncWithCallbackPathHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange t) {
			System.out.println("/asyncWithCallback path was called.");

			SomeThirdPartyFramework.executeSomeAsyncFunctionWithNoThreadGuarantees(
				(data) -> {
					sleepForMillis(5);

					try {
						System.out.println("In async callback, about to respond with data: " + data);
						String response = "This is the asyncWithCallback path - "
										  + "data supplied by the third party framework: " + data;
						t.sendResponseHeaders(200, response.length());
						OutputStream os = t.getResponseBody();
						os.write(response.getBytes());
						os.close();
					}
					catch (IOException e) {
						System.err.println("An error occurred while processing the request.");
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			);
		}

		// NOTE: FOR THE PURPOSE OF THIS EXAMPLE YOU ARE NOT ALLOWED TO MODIFY THIS CLASS!
		static class SomeThirdPartyFramework {

			// Imagine this executor is buried somewhere deep in third party code - you cannot specify which Executor
			// 		is used, and you cannot wrap it.
			private static final Executor executorYouCannotWrap = Executors.newCachedThreadPool();

			static void executeSomeAsyncFunctionWithNoThreadGuarantees(SomeThirdPartyCallback callback) {
				String dataFromThirdPartyFramework = "3rd-party-framework-data_" + UUID.randomUUID().toString();
				// Do the callback in an async thread outside the control of the caller.
				executorYouCannotWrap.execute(() -> callback.doTheCallback(dataFromThirdPartyFramework));
			}
		}

		// NOTE: FOR THE PURPOSE OF THIS EXAMPLE YOU ARE NOT ALLOWED TO MODIFY THIS CLASS!
		interface SomeThirdPartyCallback {
			void doTheCallback(String data);
		}
	}

	static void sleepForMillis(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

}
