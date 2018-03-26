package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

/**
 * Common interface for outgoing requests which include tagging. Not to be directly used by SDK user.
 */
public interface OutgoingTaggable {

	String getDynatraceStringTag();

	byte[] getDynatraceByteTag();

}