package com.example.gsb.membership;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;

class DuplicateChangeTest {

    private MembershipManager manager;

    @BeforeEach
    void setUp() {
        manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), Clock.systemUTC(), Set.of("A", "B", "C"));
    }

    @Test
    void rejectsSecondChangeWhileOneIsInProgress() {
        manager.proposeChange("add-D", Set.of("A", "B", "C", "D"));
        try {
            manager.proposeChange("add-E", Set.of("A", "B", "C", "E"));
            throw new AssertionError("expected ChangeInProgressException");
        } catch (ChangeInProgressException expected) {
            // 一次只允许一个变更
        }
    }

    @Test
    void rejectsDuplicateSubmissionOfSameChangeId() {
        manager.proposeChange("change-42", Set.of("A", "B", "C", "D"));
        manager.commitChange(Set.of("A", "B", "C", "D"));

        // 即使上一变更已完成，相同 changeId 的重复提交也必须被识别并拒绝
        try {
            manager.proposeChange("change-42", Set.of("A", "B", "C", "D", "E"));
            throw new AssertionError("expected DuplicateChangeException");
        } catch (DuplicateChangeException expected) {
            // 重复提交被拒绝
        }
    }

    @Test
    void rejectsDuplicateResubmissionAfterRecovery() {
        InMemoryMembershipStore store = new InMemoryMembershipStore();
        MembershipManager before = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));
        before.proposeChange("change-42", Set.of("A", "B", "C", "D"));

        MembershipManager after = MembershipManager.recover(store, Clock.systemUTC());
        try {
            after.proposeChange("change-42", Set.of("A", "B", "C", "D"));
            throw new AssertionError("expected rejection");
        } catch (MembershipChangeException expected) {
            // 崩溃恢复后在途变更仍然占位：相同提交被拒（在途变更优先报 ChangeInProgress）
        }
    }
}
