package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.OutgoingTaggable;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public abstract class AbstractOutgoingTaggable implements OutgoingTaggable {

	private String stringTag;
	private byte[] byteTag;

	@Override
	public String getDynatraceStringTag() {
		return stringTag;
	}

	@Override
	public byte[] getDynatraceByteTag() {
		return byteTag;
	}

}