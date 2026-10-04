package com.example.gsb.membership;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 安全性验证：联合阶段不得出现新旧配置各自形成多数派并作出冲突决议。
 *
 * <p>核心性质：联合共识下任何两个被接受的法定人数必然相交，因此两个冲突决议
 * 不可能同时通过。这里用场景测试与穷举测试双重证明。</p>
 */
class JointConsensusSafetyTest {

    /**
     * 场景：旧配置 {A,B,C}，新配置 {C,D,E}，交集只有 C。
     * 若一次性替换配置，{A,B} 与 {D,E} 互不相交，可各自形成多数派导致脑裂。
     * 联合共识下：{A,B} 只是旧配置多数派、{D,E} 只是新配置多数派，均被拒绝；
     * 只有同时覆盖两边多数的投票（如 {A,B,D,E} 或含 C 的组合）才被接受。
     */
    @Test
    void disjointOldAndNewMajoritiesCannotBothDecide() {
        MembershipManager manager = MembershipManager.bootstrap(
                new InMemoryMembershipStore(), Clock.systemUTC(), Set.of("A", "B", "C"));
        manager.proposeChange("replace", Set.of("C", "D", "E"));

        // 旧配置多数派 {A,B}：在新配置中只有 0 票，不能单独决策
        assertThat(manager.decide(Set.of("A", "B"))).isFalse();
        // 新配置多数派 {D,E}：在旧配置中只有 0 票，不能单独决策
        assertThat(manager.decide(Set.of("D", "E"))).isFalse();
        // 即使各自拿到交集节点 C，{A,B,C} 在新配置中仅 1/3 票，{C,D,E} 在旧配置中仅 1/3 票
        assertThat(manager.decide(Set.of("A", "B", "C"))).isFalse();
        assertThat(manager.decide(Set.of("C", "D", "E"))).isFalse();
        // 同时满足两份配置多数派的投票才被接受
        assertThat(manager.decide(Set.of("A", "B", "D", "E"))).isTrue();
        assertThat(manager.decide(Set.of("A", "C", "D", "E"))).isTrue();

        // 联合阶段没有任何决议被单方多数派通过
        assertThat(manager.metrics().jointDecisions()).isEqualTo(2);
    }

    /**
     * 穷举证明：对多组新旧配置，枚举全部投票子集，
     * 任意两个被联合规则接受的法定人数必然相交 —— 冲突决议无法同时成立。
     */
    @Test
    void anyTwoAcceptedJointQuorumsIntersect() {
        List<ClusterConfiguration> configs = List.of(
                ClusterConfiguration.joint(Set.of("A", "B", "C"), Set.of("C", "D", "E"), 1),
                ClusterConfiguration.joint(Set.of("A", "B", "C"), Set.of("A", "B", "C", "D"), 1),
                ClusterConfiguration.joint(Set.of("A", "B", "C", "D", "E"), Set.of("C", "D", "E", "F", "G"), 1),
                ClusterConfiguration.joint(Set.of("A", "B", "C"), Set.of("A", "B"), 1));

        for (ClusterConfiguration config : configs) {
            List<Set<String>> quorums = acceptedQuorums(config);
            assertThat(quorums).isNotEmpty();
            for (Set<String> q1 : quorums) {
                for (Set<String> q2 : quorums) {
                    Set<String> intersection = new LinkedHashSet<>(q1);
                    intersection.retainAll(q2);
                    assertThat(intersection)
                            .as("joint quorums %s and %s of %s must intersect", q1, q2, config)
                            .isNotEmpty();
                }
            }
        }
    }

    /** 对照组：若绕过联合共识直接一次性替换配置，两个不相交多数派可同时成立（脑裂风险）。 */
    @Test
    void atomicReplacementWouldAllowDisjointMajorities() {
        ClusterConfiguration oldConfig = ClusterConfiguration.stable(Set.of("A", "B", "C"), 0);
        ClusterConfiguration newConfig = ClusterConfiguration.stable(Set.of("D", "E", "F"), 1);

        // 同一时刻，旧配置的多数派与新配置的多数派互不相交 —— 正是一次性替换的危险所在
        assertThat(oldConfig.isQuorum(Set.of("A", "B"))).isTrue();
        assertThat(newConfig.isQuorum(Set.of("D", "E"))).isTrue();
    }

    private static List<Set<String>> acceptedQuorums(ClusterConfiguration config) {
        Set<String> universe = new LinkedHashSet<>(config.oldMembers());
        universe.addAll(config.newMembers());
        List<Set<String>> quorums = new ArrayList<>();
        for (Set<String> subset : powerSet(new ArrayList<>(universe))) {
            if (config.isQuorum(subset)) {
                quorums.add(subset);
            }
        }
        return quorums;
    }

    private static List<Set<String>> powerSet(List<String> elements) {
        List<Set<String>> result = new ArrayList<>();
        int total = 1 << elements.size();
        for (int mask = 0; mask < total; mask++) {
            Set<String> subset = new LinkedHashSet<>();
            for (int i = 0; i < elements.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(elements.get(i));
                }
            }
            result.add(subset);
        }
        return result;
    }
}
