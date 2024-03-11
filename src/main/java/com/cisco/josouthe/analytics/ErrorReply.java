package com.cisco.josouthe.analytics;

public class ErrorReply {
    public int statusCode;
    public String code, message, developerMessage;
    public boolean isErrorResponse() { return ( message != null && statusCode > 299); }
}
