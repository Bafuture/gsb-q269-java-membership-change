package com.example.gsb.membership;

/** 成员变更相关异常的基类。 */
public class MembershipException extends RuntimeException {

    public MembershipException(String message) {
        super(message);
    }
}
