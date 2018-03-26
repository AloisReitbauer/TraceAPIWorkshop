package com.dynatrace.oneagent.sdk;

import com.dynatrace.oneagent.sdk.api.OneAgentSDK;
import com.dynatrace.oneagent.sdk.dummyimpl.OneAgentSDKDummyImpl;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class OneAgentSDKFactory {

	/**
	 * This method returns an instance of the OneAgent SDK.
	 */
	public static OneAgentSDK createInstance() {
		return new OneAgentSDKDummyImpl();
	}
}
