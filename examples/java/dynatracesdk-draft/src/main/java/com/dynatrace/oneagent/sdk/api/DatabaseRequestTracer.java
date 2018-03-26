package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

public interface DatabaseRequestTracer extends Tracer {

	/**
	 * Adds optional information about retrieved rows of the traced database request. Must be set before end() 
	 * of this tracer is being called.
	 * 
	 * @param rowsReturned number of rows returned by this traced database request. Only positive values are allowed. 
	 */
	public void setRowsReturned(int rowsReturned);
	
	/**
	 * Adds optional information about round-trip count to database server. Must be set before end() 
	 * of this tracer is being called.
	 * 
	 * @param roundTripCount count of round-trips that took place. Only positive values are allowed.
	 */
	public void setRoundTripCount(int roundTripCount);
	
}
