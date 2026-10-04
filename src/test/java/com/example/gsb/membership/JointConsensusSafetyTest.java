package com.example.gsb.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 安全性验证：最极端的整组替换场景，旧配置 {A,B,C} 与新配置 {D,E,F} 完全不相交。
 * 联合共识必须保证旧配置单独的多数派、新配置单独的多数派都无法决议，
 * 任意两个被接受的投票集合必然相交，因此不可能出现两个多数派各自作出冲突决议。
 */
class JointConsensusSafetyTest {

    private static final Set<NodeId> OLD = Set.of(NodeId.of("A"), NodeId.of("B"), NodeId.of("C"));
    private static final Set<NodeId> NEW = Set.of(NodeId.of("D"), NodeId.of("E"), NodeId.of("F"));
    private static final Set<NodeId> ALL = Set.of(NodeId.of("A"), NodeId.of("B"), NodeId.of("C"),
            NodeId.of("D"), NodeId.of("E"), NodeId.of("F"));

    private MembershipManager jointManager() {
        MembershipManager manager = MembershipManager.bootstrap(new InMemoryMembershipStore(), OLD);
        manager.proposeChange(NEW);
        return manager;
    }

    @Test
    void neitherOldNorNewMajorityAloneCanDecide() {
        MembershipManager manager = jointManager();

        // 旧配置多数派（3 票中的 2 票）：新配置 0 票 → 拒绝
        assertThat(manager.decide(Set.of(NodeId.of("A"), NodeId.of("B")))).isFalse();
        // 新配置多数派（3 票中的 2 票）：旧配置 0 票 → 拒绝
        assertThat(manager.decide(Set.of(NodeId.of("D"), NodeId.of("E")))).isFalse();
        // 只有同时凑齐两边的多数派才能决议
        assertThat(manager.decide(Set.of(NodeId.of("A"), NodeId.of("B"),
                NodeId.of("D"), NodeId.of("E")))).isTrue();

        // 被拒绝的决议不计入联合阶段决议次数
        assertThat(manager.stats().jointDecisions()).isEqualTo(1);
    }

    @Test
    void conflictingProposalsFromSeparateMajoritiesAreBothRejected() {
        MembershipManager manager = jointManager();

        // 旧配置的多数派想通过 X，新配置的多数派想通过冲突的 Y：
        // 两者都不满足“同时属于两边多数派”，都无法生效，冲突决议不可能发生
        boolean oldMajorityDecisionX = manager.decide(Set.of(NodeId.of("A"), NodeId.of("B")));
        boolean newMajorityDecisionY = manager.decide(Set.of(NodeId.of("D"), NodeId.of("E")));

        assertThat(oldMajorityDecisionX).isFalse();
        assertThat(newMajorityDecisionY).isFalse();
        assertThat(manager.stats().jointDecisions()).isZero();

        // 联合多数派（两边各至少 2 票）达成的决议才被接受
        assertThat(manager.decide(Set.of(NodeId.of("A"), NodeId.of("C"),
                NodeId.of("E"), NodeId.of("F")))).isTrue();
    }

    /**
     * 穷举 6 个节点的全部 64 个投票子集：
     * 任何两个被联合共识接受的投票集合都必须相交。
     * 这就是“不会出现两个多数派各自决策”的形式化保证——
     * 同一批节点对两个冲突决议不可能同时投出合法多数票。
     */
    @Test
    void anyTwoAcceptedVoteSetsIntersect() {
        MembershipManager manager = jointManager();
        List<Set<NodeId>> accepted = new ArrayList<>();

        for (Set<NodeId> subset : allSubsets(ALL)) {
            if (manager.decide(subset)) {
                accepted.add(subset);
            }
        }

        assertThat(accepted).isNotEmpty();
        for (int i = 0; i < accepted.size(); i++) {
            for (int j = i + 1; j < accepted.size(); j++) {
                Set<NodeId> intersection = new HashSet<>(accepted.get(i));
                intersection.retainAll(accepted.get(j));
                assertThat(intersection)
                        .as("accepted joint quorums %s and %s must intersect",
                                accepted.get(i), accepted.get(j))
                        .isNotEmpty();
            }
        }
    }

    /**
     * 对照场景：如果不走联合共识、整组一次性切换（旧集群与新集群各自行使多数派），
     * 旧多数派 {A,B} 与新多数派 {D,E} 互不相交，却能各自通过决议——这正是要避免的
     * 脑裂风险。该测试用朴素多数派规则演示该风险确实存在，从而证明联合共识的必要性。
     */
    @Test
    void oneShotReplacementWouldAllowTwoDisjointMajorities() {
        ClusterConfiguration oldConfig = new ClusterConfiguration(1, OLD);
        ClusterConfiguration newConfig = new ClusterConfiguration(2, NEW);

        Set<NodeId> oldVoters = Set.of(NodeId.of("A"), NodeId.of("B"));
        Set<NodeId> newVoters = Set.of(NodeId.of("D"), NodeId.of("E"));

        assertThat(oldConfig.isQuorum(oldVoters)).isTrue();
        assertThat(newConfig.isQuorum(newVoters)).isTrue();

        Set<NodeId> intersection = new HashSet<>(oldVoters);
        intersection.retainAll(newVoters);
        assertThat(intersection).as("naive one-shot switch can produce disjoint majority sets")
                .isEmpty();
    }

    private static List<Set<NodeId>> allSubsets(Set<NodeId> universe) {
        List<NodeId> nodes = List.copyOf(universe);
        List<Set<NodeId>> result = new ArrayList<>();
        for (int mask = 0; mask < (1 << nodes.size()); mask++) {
            Set<NodeId> subset = new HashSet<>();
            for (int i = 0; i < nodes.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(nodes.get(i));
                }
            }
            result.add(subset);
        }
        return result;
    }
}
