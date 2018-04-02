package com.dynatrace.oneagent.sdk.api;

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
public interface OneAgentSDK {
	public static final String DYNATRACE_HTTP_HEADERNAME = "X-dynaTrace";
	

	// ***** Web Server Initialization *****

	/**
	 * Initializes a WebApplicationInfo instance that is required for tracing incoming web requests. This information determines the identity and name of the resulting Web Request service in dynatrace.
	 * Also see https://www.dynatrace.com/support/help/server-side-services/introduction/how-does-dynatrace-detect-and-name-services/#web-request-services for detail description of the meaning of the parameters.
	 *
	 * @param webServerName		logical name of the web server. In case of a cluster every node in the cluster must report the same name here.
	 * 							Attention: Make sure not to use the host header for this parameter. Host headers are often spoofed and contain things like google or baidoo which do not reflect your setup.
	 * @param applicationID		application ID of the web application
	 * @param contextRoot		context root of the application.
	 * 							All URLs traced with the returned WebApplicationInfo, should start with provided context root. 
	 * @return					{@link WebApplicationInfo} instance to work with
	 */
	WebApplicationInfo createWebApplicationInfo(String webServerName, String applicationID, String contextRoot);

	// ***** Database Initialization *****

	/**
	 * Initializes a DatabaseInfo instance that is required for tracing database requests.
	 *
	 * @param name				name of the database
	 * @param vendor			database vendor name (e.g. Oracle, MySQL, ...), can be a user defined name
	 *                          If possible use a constant defined in com.dynatrace.oneagent.sdk.api.enums.DatabaseVendor
	 * @param channelType		communication protocol used to communicate with the database.
	 * @param channelEndpoint	this represents the communication endpoint for the database. This information allows Dynatrace to tie the database requests to a specific process or cloud service. It is optional.
	 * 							* for TCP/IP: host name/IP of the server-side (can include port in the form of "host:port") 
	 * 							* for UNIX domain sockets: name of domain socket file
	 * 							* for named pipes: name of pipe
	 * @return					{@link DatabaseInfo} instance to work with
	 */
	DatabaseInfo createDatabaseInfo(String name, String vendor, ChannelType channelType, String channelEndpoint);

	// ***** Web Requests (incoming) *****

	/**
	 * Traces an incoming web request.
	 *
	 * @param webApplicationInfo	information about web application
	 * @param url					(parts of a) URL, which will be parsed into: scheme, hostname/port, path & query
	 * 								Note: the hostname will be resolved by the Agent at start() call
	 * @param method				HTTP request method
	 * @return						{@link IncomingWebRequestTracer} to work with
	 */
	IncomingWebRequestTracer traceIncomingWebRequest(WebApplicationInfo webApplicationInfo, String url, String method);

	/**
	 * Traces an outgoing web request.
	 *
	 * @param url					(parts of a) URL, which will be parsed into: scheme, hostname/port, path & query
	 * 								Note: the hostname will be resolved by the Agent at start() call
	 * @param method				HTTP request method
	 * @return						{@link OutgoingWebRequestTracer} to work with
	 */
	OutgoingWebRequestTracer traceOutgoingWebRequest(String url, String method);

	// ***** Database Calls (outgoing only) *****

	/**
	 * Traces an (outgoing) SQL database request.
	 *
	 * @param databaseInfo			information about database
	 * @param statement				database SQL statement
	 * @return						{@link DatabaseRequestTracer} to work with
	 */
	DatabaseRequestTracer traceSQLDatabaseRequest(DatabaseInfo databaseInfo, String statement);

	// ***** Remote Calls (outgoing & incoming) *****

	/**
	 * Traces an outgoing remote call.
	 *
	 * @param serviceMethod		name of the called remote method
	 * @param serviceName		name of the remote service
	 * @param serviceEndpoint	logical deployment endpoint on the server side
	 *                          In case of a clustered/load balanced service, the serviceEndpoint represents the common logical endpoint (e.g. registry://staging-environment/myservices/serviceA) where as the @channelEndpoint represents the actual communication endpoint. As such a single serviceEndpoint can have many channelEndpoints.
	 * @param channelType		communication protocol used by remote call
	 * @param channelEndpoint	this represents the communication endpoint for the remote service. This information allows Dynatrace to tie the database requests to a specific process or cloud service. It is optional.
	 * 							* for TCP/IP: host name/IP of the server-side (can include port)
	 * 							* for UNIX domain sockets: path of domain socket file
	 * 							* for named pipes: name of pipe
	 * @return					{@link OutgoingRemoteCallTracer} instance to work with
	 */
	OutgoingRemoteCallTracer traceOutgoingRemoteCall(String serviceMethod, String serviceName, String serviceEndpoint, ChannelType channelType, String channelEndpoint);

	/**
	 * Traces an incoming remote call.
	 *
	 * @param serviceMethod		name of the called remote method
	 * @param serviceName		name of the remote service
	 * @param serviceEndpoint	logical deployment endpoint on the server side
	 *                          In case of a clustered/load balanced service, the serviceEndpoint represents the common logical endpoint (e.g. registry://staging-environment/myservices/serviceA). As such a single serviceEndpoint can have many processes on many hosts that services requests for it.
	 * @return					{@link IncomingRemoteCallTracer} instance to work with
	 */
	IncomingRemoteCallTracer traceIncomingRemoteCall(String serviceMethod, String serviceName, String serviceEndpoint);

	// ***** Custom request attributes *****
	/**
	 * Adds a custom request attribute to currently traced service call. Might be called multiple times, to add more than one attribute.
	 * Check via {@link #setLoggingCallback(LoggingCallback)} if error happened. If two attributes with same key are set, both 
	 * attribute-values are captured. 
	 * 
	 * @param key 				key of the attribute. required parameter. 
	 * 							allowed keys: [a-zA-Z][a-zA-Z0-9_-]*
	 * @param value 			value of the attribute. required parameter.
	 * 							all UTF-8 representable characters are allowed.
	 */
	void addCustomRequestAttribute(String key, String value);

	/**
	 * Does exactly the same as {@link #addCustomRequestAttribute(String, String)}, but request-attribute type integer.
	 */
	void addCustomRequestAttribute(String key, int value);
	
	/**
	 * Does exactly the same as {@link #addCustomRequestAttribute(String, String)}, but request-attribute type double.
	 */
	void addCustomRequestAttribute(String key, double value);
	
	// ***** various *****

	/**
     * Returns the current SDKState. See {@link SDKState} for details.
     *
     * @return current state - never null.
     */
    SDKState getCurrentState();

    /**
     * Installs a callback that gets informed, if any SDK action has failed. For details see {@link LoggingCallback} interface. The
     * provided callback must be thread-safe, when using this {@link OneAgentSDK} instance in multithreaded environments.
     *
     * @param loggingCallback            may be null, to remove current callback. provided callback replaces any previously set callback.
     */
    void setLoggingCallback(LoggingCallback loggingCallback);

}
