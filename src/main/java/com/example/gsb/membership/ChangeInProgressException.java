package com.example.gsb.membership;

/** 已有变更在联合阶段进行中时，再次提交新变更被拒绝。 */
public class ChangeInProgressException extends MembershipChangeException {

    public ChangeInProgressException(String message) {
        super(message);
    }
}
