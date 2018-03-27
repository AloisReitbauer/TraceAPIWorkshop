package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.OutgoingWebRequestTracer;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class OutgoingWebRequestTracerImpl extends AbstractOutgoingTaggable implements OutgoingWebRequestTracer {

	/**
	 * @param url            full URL including querystring (e.g. "https://mydomain.org/path?myargument=123". never null.
	 * @param method         http method. never null.
	 */
	public OutgoingWebRequestTracerImpl(String url, String method) { }

	@Override
	public void start() { }

	@Override
	public void end() { }

	@Override
	public void error(String message) { }

	@Override
	public void error(Throwable throwable) { }

	@Override
	public void addRequestHeader(String name, String value) { }

	@Override
	public void addParameter(String name, String value) { }

	@Override
	public void addResponseHeader(String name, String value) { }

	@Override
	public void setStatusCode(int statusCode) { }
}
