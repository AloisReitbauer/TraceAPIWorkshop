package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

public interface IncomingWebRequestTracer extends Tracer, IncomingTaggable {

	/**
	 * Validates and sets the remote IP address of the incoming web request. This information is very useful to gain information about Load balancers, Proxies and ultimately the end user that is sending the request.
	 *
	 * @param remoteAddress		remote IP address
	 */
	void setRemoteAddress(String remoteAddress);

	/**
	 * All HTTP request headers should be provided to this method. Selective capturing will be done based on sensor configuration.
	 *
	 * @param name		HTTP request header field name
	 * @param value		HTTP request header field value
	 */
	void addRequestHeader(String name, String value);

	/**
	 * All HTTP parameters should be provided to this method. Selective capturing will be done based on sensor configuration.
	 *
	 * @param name		HTTP parameter name
	 * @param value		HTTP parameter value
	 */
	void addParameter(String name, String value);

	/**
	 * All HTTP response headers should be provided to this method. Selective capturing will be done based on sensor configuration.
	 *
	 * @param name		HTTP response header field name
	 * @param value		HTTP response header field value
	 */
	void addResponseHeader(String name, String value);

	/**
	 * Sets the HTTP response status code.
	 *
	 * @param statusCode		HTTP status code returned to client
	 */
	void setStatusCode(int statusCode);

}
