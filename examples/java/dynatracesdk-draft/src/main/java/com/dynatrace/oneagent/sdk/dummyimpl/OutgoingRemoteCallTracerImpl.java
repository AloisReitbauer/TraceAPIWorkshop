package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.OutgoingRemoteCallTracer;
import com.dynatrace.oneagent.sdk.api.enums.ChannelType;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class OutgoingRemoteCallTracerImpl extends AbstractOutgoingTaggable implements OutgoingRemoteCallTracer {

	public OutgoingRemoteCallTracerImpl(String serviceMethod, String serviceName, String serviceEndpoint, ChannelType channelType, String channelEndpoint) { }

	@Override
	public void start() { }

	@Override
	public void end() { }

	@Override
	public void error(String message) { }

	@Override
	public void error(Throwable throwable) { }
	
	@Override
	public void setProtocolName(String protocolName) { }

}
