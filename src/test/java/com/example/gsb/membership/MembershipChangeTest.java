package com.example.gsb.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MembershipChangeTest {

    private static final NodeId A = NodeId.of("A");
    private static final NodeId B = NodeId.of("B");
    private static final NodeId C = NodeId.of("C");
    private static final NodeId D = NodeId.of("D");

    private MembershipManager manager() {
        return MembershipManager.bootstrap(new InMemoryMembershipStore(), Set.of(A, B, C));
    }

    @Test
    void addsMemberWithNewVersionAndJointQuorum() {
        MembershipManager manager = manager();

        MembershipState joint = manager.proposeChange(Set.of(A, B, C, D));

        assertThat(joint.version()).isEqualTo(2);
        assertThat(joint.phase()).isEqualTo(MembershipPhase.JOINT);
        assertThat(joint.oldMembers()).containsExactlyInAnyOrder(A, B, C);
        assertThat(joint.newMembers()).containsExactlyInAnyOrder(A, B, C, D);

        // 旧配置多数派（2 票）但不满足新配置多数派（需 3 票）→ 拒绝
        assertThat(manager.decide(Set.of(A, B))).isFalse();
        // 两份配置的多数派同时满足 → 接受
        assertThat(manager.decide(Set.of(A, B, C))).isTrue();

        MembershipState stable = manager.commit();
        assertThat(stable.version()).isEqualTo(2);
        assertThat(stable.phase()).isEqualTo(MembershipPhase.STABLE);
        assertThat(stable.oldMembers()).containsExactlyInAnyOrder(A, B, C, D);

        // 稳定期只需新（现已生效的）配置多数派，即 4 节点中的 3 票
        assertThat(manager.decide(Set.of(A, B, C))).isTrue();
        assertThat(manager.decide(Set.of(A, B))).isFalse();
    }

    @Test
    void removesMemberAndRequiresJointQuorumDuringTransition() {
        MembershipManager manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), Set.of(A, B, C, D));

        manager.proposeChange(Set.of(A, B, C));

        // 新配置 3 节点需 2 票，但旧配置 4 节点需 3 票 → 仅 2 票仍被拒绝
        assertThat(manager.decide(Set.of(A, B))).isFalse();
        assertThat(manager.decide(Set.of(A, B, C))).isTrue();

        manager.commit();
        assertThat(manager.state().oldMembers()).containsExactlyInAnyOrder(A, B, C);
        assertThat(manager.state().version()).isEqualTo(2);

        // 移除完成后，2 票即构成多数派
        assertThat(manager.decide(Set.of(A, B))).isTrue();
    }

    @Test
    void rejectsSecondChangeWhileOneInProgress() {
        MembershipManager manager = manager();
        manager.proposeChange(Set.of(A, B, C, D));

        assertThatThrownBy(() -> manager.proposeChange(Set.of(A, B, C, D)))
                .isInstanceOf(ChangeInProgressException.class)
                .hasMessageContaining("already in progress");
        // 换成另一个目标同样拒绝
        assertThatThrownBy(() -> manager.proposeChange(Set.of(A, B)))
                .isInstanceOf(ChangeInProgressException.class);
    }

    @Test
    void rejectsDuplicateSubmissionIdenticalToCurrentConfig() {
        MembershipManager manager = manager();

        // 从未开始变更时提交与当前一致的配置：识别为重复/空变更
        assertThatThrownBy(() -> manager.proposeChange(Set.of(A, B, C)))
                .isInstanceOf(DuplicateChangeException.class);

        manager.proposeChange(Set.of(A, B, C, D));
        manager.commit();
        // 完成后再次提交完全相同的成员列表：重复
        assertThatThrownBy(() -> manager.proposeChange(Set.of(A, B, C, D)))
                .isInstanceOf(DuplicateChangeException.class);
    }

    @Test
    void rejectsInvalidTargetsAndCommitWithoutChange() {
        MembershipManager manager = manager();

        assertThatThrownBy(() -> manager.proposeChange(Set.of()))
                .isInstanceOf(InvalidConfigurationException.class);
        assertThatThrownBy(manager::commit)
                .isInstanceOf(NoChangeInProgressException.class);
    }

    @Test
    void reportsStatsAcrossTheChange() {
        MutableClock clock = new MutableClock(1_000);
        MembershipManager manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), clock, Set.of(A, B, C));

        MembershipStats before = manager.stats();
        assertThat(before.currentVersion()).isEqualTo(1);
        assertThat(before.phase()).isEqualTo(MembershipPhase.STABLE);
        assertThat(before.oldConfigSize()).isEqualTo(3);
        assertThat(before.newConfigSize()).isZero();
        assertThat(before.jointDecisions()).isZero();
        assertThat(before.lastChangeDurationMillis()).isNull();

        clock.advance(50);
        manager.proposeChange(Set.of(A, B, C, D));

        MembershipStats during = manager.stats();
        assertThat(during.phase()).isEqualTo(MembershipPhase.JOINT);
        assertThat(during.oldConfigSize()).isEqualTo(3);
        assertThat(during.newConfigSize()).isEqualTo(4);

        assertThat(manager.decide(Set.of(A, B))).isFalse();
        assertThat(manager.decide(Set.of(A, B, C))).isTrue();
        assertThat(manager.decide(Set.of(A, C, D))).isTrue();

        assertThat(manager.stats().jointDecisions()).isEqualTo(2);

        clock.advance(100);
        manager.commit();

        MembershipStats after = manager.stats();
        assertThat(after.currentVersion()).isEqualTo(2);
        assertThat(after.phase()).isEqualTo(MembershipPhase.STABLE);
        assertThat(after.oldConfigSize()).isEqualTo(4);
        assertThat(after.newConfigSize()).isZero();
        assertThat(after.jointDecisions()).isZero();
        assertThat(after.changesCompleted()).isEqualTo(1);
        assertThat(after.lastChangeDurationMillis()).isEqualTo(100);
    }
}
