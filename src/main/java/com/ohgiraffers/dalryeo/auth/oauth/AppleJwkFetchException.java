package com.ohgiraffers.dalryeo.auth.oauth;

class AppleJwkFetchException extends IllegalStateException {

    AppleJwkFetchException(String message) {
        super(message);
    }

    AppleJwkFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
