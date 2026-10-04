package com.example.gsb.membership;

/** 没有变更在进行中却尝试提交（commit）时被拒绝。 */
public class NoChangeInProgressException extends MembershipChangeException {

    public NoChangeInProgressException(String message) {
        super(message);
    }
}
