package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.IncomingRemoteCallTracer;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class IncomingRemoteCallTracerImpl extends AbstractIncomingTaggable implements IncomingRemoteCallTracer {

	public IncomingRemoteCallTracerImpl(String serviceMethod, String serviceName, String serviceEndpoint) { }

	@Override
	public void start() { }

	@Override
	public void end() { }

	@Override
	public void error(String message) { }

	@Override
	public void setProtocolName(String protocolName) { }

}
