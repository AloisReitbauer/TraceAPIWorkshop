package com.dynatrace.oneagent.sdk.api;

/*
 * ============================================================================================================
 * This API of the Dynatrace SDK is a DRAFT. It's not guaranteed that the final API will look exactly the same.
 * The implementation in this state is *non-functional* It's only exposed for demo-purposes.
 * ============================================================================================================
 */

/**
 * Logging-Callback gets called only inside a OneAgentSDK API call when error/warning has occurred. <br>
 * Never call any SDK API, when inside one of this callback methods.
 */
public interface LoggingCallback {

    /**
     * just warning. something is missing, but agent is working normal.
     *
     * @param message            message text. never null.
     */
    void warn(String message);

    /**
     * something that should be done can't be done. (e. g. path couldn't be
     * started)
     *
     * @param message            message text. never null.
     */
    void error(String message);

}
