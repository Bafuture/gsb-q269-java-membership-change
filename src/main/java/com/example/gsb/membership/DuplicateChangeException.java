package com.example.gsb.membership;

/** 重复提交（相同 changeId 的变更）被拒绝。 */
public class DuplicateChangeException extends MembershipChangeException {

    public DuplicateChangeException(String message) {
        super(message);
    }
}
