package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.IncomingTaggable;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public abstract class AbstractIncomingTaggable implements IncomingTaggable {

	@Override
	public void setDynatraceStringTag(String tag) { }

	@Override
	public void setDynatraceByteTag(byte[] tag) { }

}