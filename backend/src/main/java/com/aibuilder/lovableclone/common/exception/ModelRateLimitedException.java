package com.aibuilder.lovableclone.common.exception;

// Provider ne rate limit lagayi. Yeh na caller ki galti hai na hamari, aur baad mein
// wahi request chal jayegi — isliye iska apna type hai, taaki 500 "kuch galat ho gaya"
// ke bajaye 429 aur ek kaam ka message ja sake
public class ModelRateLimitedException extends RuntimeException {
    public ModelRateLimitedException(String message, Throwable cause) {
        super(message, cause);
    }
}
