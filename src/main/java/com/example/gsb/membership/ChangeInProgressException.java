package com.example.gsb.membership;

/** 已有一次变更正在进行时，再次提交变更抛出。 */
public class ChangeInProgressException extends MembershipException {

    public ChangeInProgressException(String message) {
        super(message);
    }
}
