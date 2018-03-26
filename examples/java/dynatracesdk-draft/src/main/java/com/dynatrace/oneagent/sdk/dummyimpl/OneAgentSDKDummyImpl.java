package com.dynatrace.oneagent.sdk.dummyimpl;

import com.dynatrace.oneagent.sdk.api.*;
import com.dynatrace.oneagent.sdk.api.enums.ChannelType;
import com.dynatrace.oneagent.sdk.api.enums.SDKState;
import com.dynatrace.oneagent.sdk.api.infos.DatabaseInfo;
import com.dynatrace.oneagent.sdk.api.infos.WebApplicationInfo;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */
public class OneAgentSDKDummyImpl implements OneAgentSDK {

	@Override
	public WebApplicationInfo createWebApplicationInfo(String webServerName, String applicationID, String contextRoot) {
		return new WebApplicationInfoImpl(webServerName, applicationID, contextRoot);
	}

	@Override
	public DatabaseInfo createDatabaseInfo(String name, String vendor, ChannelType protocol, String physicalEndpoint) {
		return new DatabaseImpl(name, vendor, protocol, physicalEndpoint);
	}

	@Override
	public IncomingWebRequestTracer traceIncomingWebRequest(WebApplicationInfo webApplicationInfo, String url, String method) {
		return new IncomingWebRequestTracerImpl(url, method);
	}

	@Override
	public DatabaseRequestTracer traceSQLDatabaseRequest(DatabaseInfo databaseConfig, String statement) {
		return new DatabaseRequestTracerImpl(statement);
	}

	@Override
	public OutgoingRemoteCallTracer traceOutgoingRemoteCall(String serviceMethod, String serviceName, String serviceEndpoint, ChannelType channelType, String channelEndpoint) {
		return new OutgoingRemoteCallTracerImpl(serviceMethod, serviceName, serviceEndpoint, channelType, channelEndpoint);
	}

	@Override
	public IncomingRemoteCallTracer traceIncomingRemoteCall(String serviceMethod, String serviceName, String serviceEndpoint) {
		return new IncomingRemoteCallTracerImpl(serviceMethod, serviceName, serviceEndpoint);
	}

	@Override
	public SDKState getCurrentState() {
		return SDKState.PERMANENTLY_INACTIVE; // this is a dummy implementation. it will not send data anywhere.
	}

	@Override
	public void setLoggingCallback(LoggingCallback loggingCallback) { }

	@Override
	public void addCustomRequestAttribute(String key, String value) { }

	@Override
	public void addCustomRequestAttribute(String key, int value) { }

	@Override
	public void addCustomRequestAttribute(String key, double value) { }

}
