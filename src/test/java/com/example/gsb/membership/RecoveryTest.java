package com.example.gsb.membership;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 变更中断恢复：用“丢弃管理器实例 + 从同一存储 recover”模拟节点崩溃重启。
 * 重启后必须按当前配置版本继续，而不是回到旧配置。
 */
class RecoveryTest {

    @Test
    void resumesJointPhaseAfterCrashInsteadOfRollingBack() {
        InMemoryMembershipStore store = new InMemoryMembershipStore();
        MembershipManager before = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));
        before.proposeChange("add-D", Set.of("A", "B", "C", "D"));
        before.decide(Set.of("A", "B", "C", "D"));

        // 节点崩溃：所有内存状态随进程消失，只剩持久化存储；随后重启恢复
        MembershipManager after = MembershipManager.recover(store, Clock.systemUTC());

        assertThat(after).isNotNull();
        // 关键断言：恢复后仍是联合阶段、版本为 1，而不是回到 v0 的旧配置
        assertThat(after.configuration().isJoint()).isTrue();
        assertThat(after.configuration().version()).isEqualTo(1);
        assertThat(after.configuration().oldMembers()).containsExactly("A", "B", "C");
        assertThat(after.configuration().newMembers()).containsExactly("A", "B", "C", "D");
        // 联合阶段的决议计数也随快照保留
        assertThat(after.metrics().jointDecisions()).isEqualTo(1);

        // 恢复后继续完成变更，不需要、也不允许重新从旧配置开始
        after.commitChange(Set.of("A", "B", "C", "D"));
        assertThat(after.configuration().version()).isEqualTo(1);
        assertThat(after.configuration().members()).containsExactly("A", "B", "C", "D");
        assertThat(after.configuration().isJoint()).isFalse();
    }

    @Test
    void restartAfterCommitKeepsNewConfigurationAndVersion() {
        InMemoryMembershipStore store = new InMemoryMembershipStore();
        MembershipManager before = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));
        before.proposeChange("remove-C", Set.of("A", "B"));
        before.commitChange(Set.of("A", "B", "C"));

        MembershipManager after = MembershipManager.recover(store, Clock.systemUTC());

        assertThat(after.configuration().isJoint()).isFalse();
        assertThat(after.configuration().version()).isEqualTo(1);
        assertThat(after.configuration().members()).containsExactly("A", "B");
    }

    @Test
    void jointQuorumRuleStillEnforcedAfterRecovery() {
        InMemoryMembershipStore store = new InMemoryMembershipStore();
        MembershipManager before = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));
        before.proposeChange("replace", Set.of("C", "D", "E"));

        MembershipManager after = MembershipManager.recover(store, Clock.systemUTC());

        // 恢复后单方多数派依然不能决策
        assertThat(after.decide(Set.of("A", "B"))).isFalse();
        assertThat(after.decide(Set.of("D", "E"))).isFalse();
        assertThat(after.decide(Set.of("A", "B", "D", "E"))).isTrue();

        // 联合多数派不足时不允许提交，中断的变更无法被“跳过”
        try {
            after.commitChange(Set.of("A", "B"));
            throw new AssertionError("expected MembershipChangeException");
        } catch (MembershipChangeException expected) {
            // 提交仍需联合多数派
        }
    }

    @Test
    void recoverFromEmptyStoreReturnsNull() {
        assertThat(MembershipManager.recover(new InMemoryMembershipStore(), Clock.systemUTC())).isNull();
    }
}
