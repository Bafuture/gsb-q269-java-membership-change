package com.example.gsb.membership;

/** 提交的变更目标与当前生效配置完全一致（无实际变化）时抛出。 */
public class DuplicateChangeException extends MembershipException {

    public DuplicateChangeException(String message) {
        super(message);
    }
}
