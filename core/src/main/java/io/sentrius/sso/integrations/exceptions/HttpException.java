package io.sentrius.sso.integrations.exceptions;

public class HttpException extends org.apache.hc.core5.http.HttpException {

    public HttpException(int code, String requestFailed) {
        super("Response Code: " + code + " occurred, with exception: " + requestFailed);
    }
}
