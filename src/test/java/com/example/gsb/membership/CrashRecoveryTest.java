package com.example.gsb.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 变更中断恢复：节点在联合阶段崩溃，重建的 manager 必须按当前配置版本继续。 */
class CrashRecoveryTest {

    private static final NodeId A = NodeId.of("A");
    private static final NodeId B = NodeId.of("B");
    private static final NodeId C = NodeId.of("C");
    private static final NodeId D = NodeId.of("D");

    @Test
    void resumesJointPhaseAtCurrentVersionAfterRestart(@TempDir Path dir) throws IOException {
        Path stateFile = dir.resolve("membership.properties");
        MutableClock clock = new MutableClock(5_000);

        MembershipManager beforeCrash = MembershipManager.bootstrap(
                new FileMembershipStore(stateFile), clock, Set.of(A, B, C));
        beforeCrash.proposeChange(Set.of(A, B, C, D));
        assertThat(beforeCrash.decide(Set.of(A, B, C))).isTrue();

        // 确认状态确实已落盘
        assertThat(Files.readString(stateFile)).contains("phase=JOINT", "version=2");

        // 节点崩溃：丢弃旧 manager，用同一个存储重启
        MembershipManager restarted = new MembershipManager(new FileMembershipStore(stateFile), clock);

        MembershipState recovered = restarted.state();
        assertThat(recovered.version()).isEqualTo(2);
        assertThat(recovered.phase()).isEqualTo(MembershipPhase.JOINT);
        assertThat(recovered.oldMembers()).containsExactlyInAnyOrder(A, B, C);
        assertThat(recovered.newMembers()).containsExactlyInAnyOrder(A, B, C, D);
        assertThat(recovered.jointDecisions()).isEqualTo(1);

        // 按当前版本继续完成变更，而不是回到旧配置
        assertThat(restarted.decide(Set.of(A, B))).isFalse();
        assertThat(restarted.decide(Set.of(A, B, C))).isTrue();

        clock.advance(250);
        MembershipState stable = restarted.commit();
        assertThat(stable.version()).isEqualTo(2);
        assertThat(stable.phase()).isEqualTo(MembershipPhase.STABLE);
        assertThat(stable.oldMembers()).containsExactlyInAnyOrder(A, B, C, D);

        // 完成后再次重启，仍然是版本 2 的新配置
        MembershipManager afterSecondRestart = new MembershipManager(
                new FileMembershipStore(stateFile), clock);
        assertThat(afterSecondRestart.state().version()).isEqualTo(2);
        assertThat(afterSecondRestart.state().oldMembers()).containsExactlyInAnyOrder(A, B, C, D);
        assertThat(afterSecondRestart.stats().changesCompleted()).isEqualTo(1);
    }

    @Test
    void recoveringJointPhaseStillRejectsConcurrentChange(@TempDir Path dir) {
        Path stateFile = dir.resolve("membership.properties");
        MembershipStore store = new FileMembershipStore(stateFile);

        MembershipManager first = MembershipManager.bootstrap(store, Set.of(A, B, C));
        first.proposeChange(Set.of(A, B, C, D));

        MembershipManager restarted = new MembershipManager(store);
        assertThatThrownBy(() -> restarted.proposeChange(Set.of(A, B)))
                .isInstanceOf(ChangeInProgressException.class);
        assertThat(restarted.state().version()).isEqualTo(2);
    }
}
