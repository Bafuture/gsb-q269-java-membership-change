package com.example.gsb.membership;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipChangeTest {

    private InMemoryMembershipStore store;
    private MembershipManager manager;

    @BeforeEach
    void setUp() {
        store = new InMemoryMembershipStore();
        manager = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));
    }

    @Test
    void bootstrapsAtVersionZero() {
        assertThat(manager.configuration().version()).isZero();
        assertThat(manager.configuration().members()).containsExactly("A", "B", "C");
        assertThat(manager.configuration().isJoint()).isFalse();
    }

    @Test
    void addsMemberThroughJointPhaseAndBumpsVersion() {
        manager.proposeChange("add-D", Set.of("A", "B", "C", "D"));

        assertThat(manager.configuration().version()).isEqualTo(1);
        assertThat(manager.configuration().isJoint()).isTrue();
        assertThat(manager.configuration().oldMembers()).containsExactly("A", "B", "C");
        assertThat(manager.configuration().newMembers()).containsExactly("A", "B", "C", "D");

        manager.commitChange(Set.of("A", "B", "C"));

        assertThat(manager.configuration().isJoint()).isFalse();
        assertThat(manager.configuration().version()).isEqualTo(1);
        assertThat(manager.configuration().members()).containsExactly("A", "B", "C", "D");
    }

    @Test
    void removesMemberThroughJointPhase() {
        manager.proposeChange("remove-C", Set.of("A", "B"));
        manager.commitChange(Set.of("A", "B", "C"));

        assertThat(manager.configuration().version()).isEqualTo(1);
        assertThat(manager.configuration().members()).containsExactly("A", "B");
    }

    @Test
    void chainsMultipleChangesWithMonotonicVersions() {
        manager.proposeChange("add-D", Set.of("A", "B", "C", "D"));
        manager.commitChange(Set.of("A", "B", "C"));

        manager.proposeChange("remove-A", Set.of("B", "C", "D"));
        manager.commitChange(Set.of("B", "C", "D"));

        assertThat(manager.configuration().version()).isEqualTo(2);
        assertThat(manager.configuration().members()).containsExactly("B", "C", "D");
    }

    @Test
    void rejectsNoOpChange() {
        try {
            manager.proposeChange("noop", Set.of("A", "B", "C"));
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 目标成员与当前相同，没有实质变更
        }
    }

    @Test
    void rejectsCommitWhenStable() {
        try {
            manager.commitChange(Set.of("A", "B", "C"));
            throw new AssertionError("expected NoChangeInProgressException");
        } catch (NoChangeInProgressException expected) {
            // 稳定期没有在途变更
        }
    }
}
