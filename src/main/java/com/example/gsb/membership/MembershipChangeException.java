package com.example.gsb.membership;

/** 成员变更被拒绝时抛出的异常。 */
public class MembershipChangeException extends RuntimeException {

    public MembershipChangeException(String message) {
        super(message);
    }
}
