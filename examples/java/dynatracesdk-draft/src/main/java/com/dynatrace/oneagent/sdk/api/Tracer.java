package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

/**
 * Common interface for timing-related methods. Not to be directly used by SDK user.
 */
public interface Tracer {
	void start();

	// only to be called once, concatenation has to be handled by user, DOES NOT end tracer
	// if multiple overloads exist, at most one of them can be called and only once
	void error(String message);
	
	void error(Throwable throwable);

	void end();

}