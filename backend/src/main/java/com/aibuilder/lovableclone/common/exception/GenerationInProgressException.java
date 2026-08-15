package com.aibuilder.lovableclone.common.exception;

// Us project pe ek generation already chal rahi hai. Caller ki galti nahi, isliye 409:
// wahi request thodi der baad chal jayegi
public class GenerationInProgressException extends RuntimeException {
    public GenerationInProgressException(String message) {
        super(message);
    }
}
