package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.IncomingWebRequestTracer;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class IncomingWebRequestTracerImpl extends AbstractIncomingTaggable implements IncomingWebRequestTracer {

	public IncomingWebRequestTracerImpl(String url, String method) { }

	@Override
	public void start() { }

	@Override
	public void end() { }

	@Override
	public void error(String message) { }

	@Override
	public void error(Throwable throwable) { }

	@Override
	public void setRemoteAddress(String remoteAddress) { }

	@Override
	public void addRequestHeader(String requestHeaderKey, String requestHeaderValue) { }

	@Override
	public void addParameter(String parameterKey, String parameterValue) { }

	@Override
	public void addResponseHeader(String responseHeaderKey, String responseHeaderValue) { }

	@Override
	public void setStatusCode(int statusCode) { }

}
