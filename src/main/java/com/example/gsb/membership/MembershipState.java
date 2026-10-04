package com.example.gsb.membership;

import java.util.Collection;
import java.util.Set;

/**
 * 成员管理的完整可持久化状态快照。
 *
 * <p>稳定期只有 {@code oldMembers} 一份配置；联合期 {@code newMembers} 非空，
 * 两份配置共用同一个版本号（本次变更的目标版本）。
 */
public record MembershipState(
        long version,
        MembershipPhase phase,
        Set<NodeId> oldMembers,
        Set<NodeId> newMembers,
        long jointDecisions,
        long changeStartMillis,
        long changesCompleted,
        long lastChangeDurationMillis) {

    public MembershipState {
        oldMembers = Set.copyOf(oldMembers);
        newMembers = Set.copyOf(newMembers);
        if (oldMembers.isEmpty()) {
            throw new InvalidConfigurationException("state must contain a non-empty current configuration");
        }
        if (phase == MembershipPhase.JOINT && newMembers.isEmpty()) {
            throw new InvalidConfigurationException("joint phase requires a non-empty new configuration");
        }
        if (phase == MembershipPhase.STABLE && !newMembers.isEmpty()) {
            throw new InvalidConfigurationException("stable phase must not carry a new configuration");
        }
    }

    /** 初始状态：版本 1 的稳定配置。 */
    public static MembershipState initial(Set<NodeId> members) {
        return new MembershipState(1, MembershipPhase.STABLE, members, Set.of(), 0, 0, 0, 0);
    }

    public boolean isJoint() {
        return phase == MembershipPhase.JOINT;
    }

    public ClusterConfiguration currentConfig() {
        return new ClusterConfiguration(version, oldMembers);
    }

    /** 联合期的新配置；稳定期调用会抛异常。 */
    public ClusterConfiguration newConfig() {
        if (!isJoint()) {
            throw new NoChangeInProgressException("no joint configuration in stable phase");
        }
        return new ClusterConfiguration(version, newMembers);
    }

    /**
     * 决议是否被接受：稳定期需满足当前配置多数派；
     * 联合期必须同时满足新旧两份配置的多数派。
     */
    public boolean accepts(Collection<NodeId> votes) {
        if (!currentConfig().isQuorum(votes)) {
            return false;
        }
        return !isJoint() || newConfig().isQuorum(votes);
    }
}
