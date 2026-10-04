package com.example.gsb.membership;

/** 在没有进行中的变更时调用 commit 抛出。 */
public class NoChangeInProgressException extends MembershipException {

    public NoChangeInProgressException(String message) {
        super(message);
    }
}
