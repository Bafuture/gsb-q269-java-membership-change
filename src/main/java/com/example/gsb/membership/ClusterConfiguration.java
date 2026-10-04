package com.example.gsb.membership;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 集群成员配置。
 *
 * <p>稳定期只含一份成员列表；联合共识（joint consensus）阶段同时持有新旧两份成员列表，
 * 此时任何决议必须同时在新、旧两份配置中各自取得多数派才算通过。</p>
 */
public final class ClusterConfiguration {

    private final long version;
    private final Set<String> oldMembers;
    private final Set<String> newMembers;
    private final boolean joint;

    private ClusterConfiguration(long version, Set<String> oldMembers, Set<String> newMembers, boolean joint) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        this.version = version;
        this.oldMembers = immutableSortedCopy(oldMembers);
        this.newMembers = immutableSortedCopy(newMembers);
        this.joint = joint;
    }

    /** 稳定配置：只有一份成员列表。 */
    public static ClusterConfiguration stable(Set<String> members, long version) {
        return new ClusterConfiguration(version, members, members, false);
    }

    /** 联合配置：新旧两份成员列表并存，version 为目标配置（新配置）的版本号。 */
    public static ClusterConfiguration joint(Set<String> oldMembers, Set<String> newMembers, long version) {
        Objects.requireNonNull(oldMembers, "oldMembers");
        Objects.requireNonNull(newMembers, "newMembers");
        if (oldMembers.equals(newMembers)) {
            throw new IllegalArgumentException("joint configuration requires different old/new members");
        }
        return new ClusterConfiguration(version, oldMembers, newMembers, true);
    }

    private static Set<String> immutableSortedCopy(Set<String> members) {
        Objects.requireNonNull(members, "members");
        if (members.isEmpty()) {
            throw new IllegalArgumentException("members must not be empty");
        }
        return Collections.unmodifiableSet(new TreeSet<>(members));
    }

    public long version() {
        return version;
    }

    public boolean isJoint() {
        return joint;
    }

    /** 旧配置成员（稳定期即当前配置）。 */
    public Set<String> oldMembers() {
        return oldMembers;
    }

    /** 新配置成员（稳定期与旧配置相同）。 */
    public Set<String> newMembers() {
        return newMembers;
    }

    /** 当前生效配置的成员（稳定期为唯一配置，联合期为旧配置）。 */
    public Set<String> members() {
        return oldMembers;
    }

    /**
     * 判断一组投票是否构成法定多数。
     *
     * <p>稳定期：唯一配置的多数派；联合期：必须同时是旧配置的多数派和新配置的多数派。
     * 这保证了任意两个合法法定人数必然相交，从而不会出现两个多数派各自决策。</p>
     */
    public boolean isQuorum(Set<String> votes) {
        Objects.requireNonNull(votes, "votes");
        if (!isMajority(votes, oldMembers)) {
            return false;
        }
        return !joint || isMajority(votes, newMembers);
    }

    static boolean isMajority(Set<String> votes, Set<String> members) {
        long count = votes.stream().filter(members::contains).count();
        return count > members.size() / 2;
    }

    @Override
    public String toString() {
        return joint
                ? "ClusterConfiguration{v" + version + ", joint, old=" + oldMembers + ", new=" + newMembers + "}"
                : "ClusterConfiguration{v" + version + ", stable, members=" + oldMembers + "}";
    }
}
