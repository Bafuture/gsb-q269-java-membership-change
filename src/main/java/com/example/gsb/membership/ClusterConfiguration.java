package com.example.gsb.membership;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** 一份带版本号的成员配置，不可变。 */
public record ClusterConfiguration(long version, Set<NodeId> members) {

    public ClusterConfiguration {
        Objects.requireNonNull(members, "members must not be null");
        if (version < 1) {
            throw new InvalidConfigurationException("configuration version must be >= 1, got " + version);
        }
        if (members.isEmpty()) {
            throw new InvalidConfigurationException("configuration must contain at least one member");
        }
        members = Set.copyOf(members);
    }

    /** 多数派所需的最小票数。 */
    public int quorumSize() {
        return members.size() / 2 + 1;
    }

    /** 判断给定投票集合是否构成本配置的多数派（只统计本配置成员，按节点去重）。 */
    public boolean isQuorum(Collection<NodeId> votes) {
        if (votes == null || votes.isEmpty()) {
            return false;
        }
        Set<NodeId> distinct = new LinkedHashSet<>(votes);
        distinct.retainAll(members);
        return distinct.size() >= quorumSize();
    }
}
