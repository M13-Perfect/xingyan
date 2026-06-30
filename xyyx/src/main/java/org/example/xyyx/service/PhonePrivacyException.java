package org.example.xyyx.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PhonePrivacyException extends RuntimeException {
    private final String code;
    private final String detailCode;

    public PhonePrivacyException(String code) {
        this(code, null);
    }

    public PhonePrivacyException(String code, String detailCode) {
        super(code);
        this.code = code;
        this.detailCode = detailCode;
    }

    public String code() {
        return code;
    }

    public String detailCode() {
        return detailCode;
    }
}
