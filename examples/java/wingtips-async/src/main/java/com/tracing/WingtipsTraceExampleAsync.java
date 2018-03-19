package com.tracing;

import com.nike.wingtips.Span;
import com.nike.wingtips.Span.SpanPurpose;
import com.nike.wingtips.Tracer;
import com.nike.wingtips.http.RequestWithHeaders;
import com.nike.wingtips.util.TracingState;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.nike.wingtips.http.HttpRequestTracingUtils.fromRequestWithHeaders;
import static com.nike.wingtips.http.HttpRequestTracingUtils.propagateTracingHeaders;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.executorServiceWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.linkTracingToCurrentThread;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.runnableWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.supplierWithTracing;
import static com.nike.wingtips.util.AsyncWingtipsHelperStatic.unlinkTracingFromCurrentThread;

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
public class WingtipsTraceExampleAsync {

	private static final Logger classLogger = LoggerFactory.getLogger(WingtipsTraceExampleAsync.class);
	private static final Logger clientLogger = LoggerFactory.getLogger("CLIENT_LOGGER");
	private static final Logger serverLogger = LoggerFactory.getLogger("SERVER_LOGGER");

	private static final Tracer wingtipsTracer = Tracer.getInstance();

	public static void main(String[] args) {

		try {
			classLogger.info("WINGTIPS EXAMPLE - ASYNC");
			initServer();
			initClient();
		} catch (Exception ex) {
			classLogger.error("Error starting example", ex);
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
					StringBuilder content = new StringBuilder();
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
		// Surround the executor with a Wingtips ExecutorServiceWithTracing so that tracing state will automatically
		//		hop threads when execute() is called.
		private static final Executor requestProcessingExecutorYouControl =
			executorServiceWithTracing(Executors.newCachedThreadPool());

		@Override
		public void handle(HttpExchange t) {
			try {
				// Start an overall request span based on the incoming request.
				//      If the request has tracing headers then create a child span, otherwise start a root span.
				startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(t);
				serverLogger.info("/asyncWithExecutor path was called.");

				TracingState requestTracingState = TracingState.getCurrentThreadTracingState();

				// This could also be solved with a Wingtips RunnableWithTracing, but for the sake of example this
				//		shows how it's done using a tracing-aware wrapped Executor.
				requestProcessingExecutorYouControl.execute(() -> {
					try {
						sleepForMillis(5);

						serverLogger.info("In async thread - about to respond to caller.");

						String response = "This is the asyncWithExecutor path";
						t.sendResponseHeaders(200, response.length());
						OutputStream os = t.getResponseBody();
						os.write(response.getBytes());
						os.close();
					}
					catch (IOException e) {
						serverLogger.error("An error occurred while processing the request.", e);
						throw new RuntimeException(e);
					}
					finally {
						// There are usually better ways to know when the request is done and trigger overall-request-span
						// 		completion, but for this simplistic example we'll have to trigger completion here.
						completeOverallRequestSpan(requestTracingState);
					}
				});
			}
			finally {
				// The overall request span will be completed on a different thread, so we need to clean up *this*
				//		thread so the next time it is used it's not polluted by a different request's tracing state.
				wingtipsTracer.unregisterFromThread();
			}
		}

	}

	static void completeOverallRequestSpan(TracingState requestTracingState) {
		// We don't know what the thread state is at this point, so we restore requestTracingState using
		//		RunnableWithTracing and complete the span once we're inside the runnable.
		runnableWithTracing(
			wingtipsTracer::completeRequestSpan,
			requestTracingState
		).run();
	}

	static class AsyncWithCompletableFuturePathHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) {
			try {
				// Start an overall request span based on the incoming request.
				//      If the request has tracing headers then create a child span, otherwise start a root span.
				startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(t);
				serverLogger.info("/asyncWithCompletableFuture path was called.");

				TracingState requestTracingState = TracingState.getCurrentThreadTracingState();

				// Could also solve this by specifying a custom ExecutorServiceWithTracing to spin the async supplier
				//		onto a thread that will automatically have the correct tracing state. But for the sake of
				//		example this shows how to easily wrap using a SupplierWithTracing.
				CompletableFuture.supplyAsync(supplierWithTracing(
					() -> {
						sleepForMillis(5);

						String dataForResponse = "cf-data_" + UUID.randomUUID().toString();
						serverLogger.info("In async CompletableFuture, about to respond with data: {}",
										  dataForResponse);

						try {
							String response = "This is the asyncWithCompletableFuture path - "
											  + "data supplied by the CompletableFuture: " + dataForResponse;
							t.sendResponseHeaders(200, response.length());
							OutputStream os = t.getResponseBody();
							os.write(response.getBytes());
							os.close();
						}
						catch (IOException e) {
							serverLogger.error("An error occurred while processing the request.", e);
							throw new RuntimeException(e);
						}

						return null;
					}
				)).whenComplete((result, ex) -> {
					// Complete the overall request span now that the CompletableFuture is done.
					completeOverallRequestSpan(requestTracingState);
				});
			}
			finally {
				// The overall request span will be completed on a different thread, so we need to clean up *this*
				//		thread so the next time it is used it's not polluted by a different request's tracing state.
				wingtipsTracer.unregisterFromThread();
			}
		}

	}

	static class AsyncWithCallbackPathHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange t) {
			try {
				// Start an overall request span based on the incoming request.
				//      If the request has tracing headers then create a child span, otherwise start a root span.
				startOverallRequestSpanAppropriatelyBasedOnIncomingRequest(t);
				serverLogger.info("/asyncWithCallback path was called.");

				TracingState requestTracingState = TracingState.getCurrentThreadTracingState();
				SomeThirdPartyFramework.executeSomeAsyncFunctionWithNoThreadGuarantees(
					(data) -> {
						sleepForMillis(5);

						// At this point we don't know what thread we'll be on thanks to the lack of guarantees
						// 		by the third party framework, so we have to reattach the correct requestTracingState
						// 		manually and "detach it" before we leave this callback by restoring this thread to its
						// 		original tracing state.
						// NOTE: This could technically be done with a delegated Wingtips ConsumerWithTracing,
						//		but there are cases where this manual linking/unlinking is necessary so for the sake
						//		of example we'll do it manually.
						TracingState originalThreadTracingState = null;
						try {
							// This links requestTracingState to the current thread, and returns the TracingState
							// 		that *was* on the thread before linkTracingToCurrentThread() was called, so that
							//		we can put it back when we're done.
							originalThreadTracingState = linkTracingToCurrentThread(requestTracingState);

							try {
								serverLogger.info("In async callback, about to respond with data: {}", data);
								String response = "This is the asyncWithCallback path - "
												  + "data supplied by the third party framework: " + data;
								t.sendResponseHeaders(200, response.length());
								OutputStream os = t.getResponseBody();
								os.write(response.getBytes());
								os.close();
							}
							catch (IOException e) {
								serverLogger.error("An error occurred while processing the request.", e);
								throw new RuntimeException(e);
							}
						}
						finally {
							// We know we've attached the request's tracing state, so we can complete the
							// 		overall request span here.
							wingtipsTracer.completeRequestSpan();

							// This replaces originalThreadTracingState so that whatever thread this callback is
							// 		running on will be restored to the same state it was when this callback was first
							// 		called.
							unlinkTracingFromCurrentThread(originalThreadTracingState);
						}
					}
				);
			}
			finally {
				// The overall request span will be completed on a different thread, so we need to clean up *this*
				//		thread so the next time it is used it's not polluted by a different request's tracing state.
				wingtipsTracer.unregisterFromThread();
			}
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
