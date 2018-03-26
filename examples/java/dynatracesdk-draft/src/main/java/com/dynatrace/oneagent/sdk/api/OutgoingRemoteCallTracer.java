package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public interface OutgoingRemoteCallTracer extends Tracer, OutgoingTaggable {

	/**
	 * Sets the name of the used remoting protocol. This is completely optional and just for display purposes.
	 *
	 * @param protocolName		protocol name
	 */
	void setProtocolName(String protocolName);

}
