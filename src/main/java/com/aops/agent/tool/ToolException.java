package com.aops.agent.tool;

/**
 * Raised when a tool fails to execute; the executor converts it into an
 * error string returned to the LLM (self-correction pattern).
 */
public class ToolException extends RuntimeException {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
