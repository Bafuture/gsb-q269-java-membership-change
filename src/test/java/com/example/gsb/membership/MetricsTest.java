package com.example.gsb.membership;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsTest {

    @Test
    void reportsVersionSizesDecisionsAndDuration() {
        ManualClock clock = new ManualClock(1_000_000);
        MembershipManager manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), clock, Set.of("A", "B", "C"));

        // 初始统计：v0，新旧配置同为 3 人，无联合阶段
        MembershipMetrics initial = manager.metrics();
        assertThat(initial.currentVersion()).isZero();
        assertThat(initial.jointInProgress()).isFalse();
        assertThat(initial.oldConfigSize()).isEqualTo(3);
        assertThat(initial.newConfigSize()).isEqualTo(3);
        assertThat(initial.jointDecisions()).isZero();
        assertThat(initial.changeDurationMillis()).isZero();

        // 进入联合阶段：3 人 -> 5 人
        manager.proposeChange("expand", Set.of("A", "B", "C", "D", "E"));
        clock.advance(120);

        MembershipMetrics during = manager.metrics();
        assertThat(during.currentVersion()).isEqualTo(1);
        assertThat(during.jointInProgress()).isTrue();
        assertThat(during.oldConfigSize()).isEqualTo(3);
        assertThat(during.newConfigSize()).isEqualTo(5);
        assertThat(during.changeDurationMillis()).isEqualTo(120);

        // 联合阶段通过两次决议、拒绝一次
        manager.decide(Set.of("A", "B", "C", "D"));
        manager.decide(Set.of("A", "B")); // 仅旧配置多数派，拒绝
        manager.decide(Set.of("B", "C", "D", "E"));
        clock.advance(80);

        MembershipMetrics afterDecisions = manager.metrics();
        assertThat(afterDecisions.jointDecisions()).isEqualTo(2);
        assertThat(afterDecisions.changeDurationMillis()).isEqualTo(200);

        // 提交后：版本 1，新旧配置同为 5 人，变更总耗时定格为 200ms
        manager.commitChange(Set.of("A", "B", "C", "D", "E"));
        clock.advance(999);

        MembershipMetrics committed = manager.metrics();
        assertThat(committed.currentVersion()).isEqualTo(1);
        assertThat(committed.jointInProgress()).isFalse();
        assertThat(committed.oldConfigSize()).isEqualTo(5);
        assertThat(committed.newConfigSize()).isEqualTo(5);
        assertThat(committed.jointDecisions()).isEqualTo(2);
        assertThat(committed.changeDurationMillis()).isEqualTo(200);
    }

    @Test
    void shrinkChangeReportsSmallerNewConfig() {
        ManualClock clock = new ManualClock(0);
        MembershipManager manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), clock, Set.of("A", "B", "C", "D", "E"));

        manager.proposeChange("shrink", Set.of("A", "B", "C"));
        MembershipMetrics metrics = manager.metrics();
        assertThat(metrics.oldConfigSize()).isEqualTo(5);
        assertThat(metrics.newConfigSize()).isEqualTo(3);
        assertThat(metrics.currentVersion()).isEqualTo(1);
    }
}
