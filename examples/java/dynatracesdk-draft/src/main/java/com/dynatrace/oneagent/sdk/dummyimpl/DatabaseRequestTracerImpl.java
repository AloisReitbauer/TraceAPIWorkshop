package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.DatabaseRequestTracer;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class DatabaseRequestTracerImpl implements DatabaseRequestTracer {

	public DatabaseRequestTracerImpl(String statement) { }

	@Override
	public void start() { }

	@Override
	public void end() { }

	@Override
	public void error(String message) { }

	@Override
	public void error(Throwable throwable) { }

	@Override
	public void setRowsReturned(int rowsReturned) { }

	@Override
	public void setRoundTripCount(int roundTripCount) { }
}
