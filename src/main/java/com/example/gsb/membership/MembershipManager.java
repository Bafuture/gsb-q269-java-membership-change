package com.example.gsb.membership;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;

/**
 * 集群成员变更管理器，实现简化的联合共识（joint consensus）流程。
 *
 * <p>一次变更的生命周期：</p>
 * <ol>
 *   <li>{@link #proposeChange(String, Set)}：稳定期校验通过后进入联合阶段，
 *       新旧两份配置并存，配置版本号 +1，并立即持久化；</li>
 *   <li>联合阶段中 {@link #decide(Set)} 的决议必须同时满足新旧两份配置的多数派；</li>
 *   <li>{@link #commitChange(Set)}：以联合多数派确认后，新配置成为唯一稳定配置，
 *       变更完成并持久化。</li>
 * </ol>
 *
 * <p>每次状态转换都先持久化再对外生效，因此节点崩溃后用 {@link #recover(MembershipStore, Clock)}
 * 重建即可按当前配置版本继续，而不会回退到旧配置。同一时刻只允许一个变更，
 * 相同 changeId 的重复提交会被识别并拒绝。</p>
 */
public final class MembershipManager {

    private final MembershipStore store;
    private final Clock clock;

    private ClusterConfiguration config;
    private long jointStartMillis;
    private long jointDecisions;
    private String lastChangeId;
    private long lastChangeMillis;

    private MembershipManager(MembershipStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 以初始成员列表（版本 0）创建管理器，并持久化初始状态。 */
    public static MembershipManager bootstrap(MembershipStore store, Clock clock, Set<String> initialMembers) {
        MembershipManager manager = new MembershipManager(store, clock);
        manager.config = ClusterConfiguration.stable(initialMembers, 0);
        manager.persist();
        return manager;
    }

    /**
     * 从持久化存储恢复管理器。若存储为空则返回 {@code null}，调用方应走
     * {@link #bootstrap(MembershipStore, Clock, Set)}；否则严格按已持久化的
     * 阶段与版本继续（联合期崩溃后仍处于联合期，不会回退到旧配置）。
     */
    public static MembershipManager recover(MembershipStore store, Clock clock) {
        MembershipState state = store.load();
        if (state == null) {
            return null;
        }
        MembershipManager manager = new MembershipManager(store, clock);
        manager.config = state.joint()
                ? ClusterConfiguration.joint(state.oldMembers(), state.newMembers(), state.version())
                : ClusterConfiguration.stable(state.oldMembers(), state.version());
        manager.jointStartMillis = state.jointStartMillis();
        manager.jointDecisions = state.jointDecisions();
        manager.lastChangeId = state.lastChangeId();
        manager.lastChangeMillis = state.lastChangeMillis();
        return manager;
    }

    /**
     * 提交一次成员变更，进入联合共识阶段。
     *
     * @param changeId   变更标识，用于识别重复提交
     * @param newMembers 目标成员列表，必须与当前成员列表不同
     * @throws ChangeInProgressException 已有变更在联合阶段进行中
     * @throws DuplicateChangeException  与在途/最近一次变更相同的 changeId 重复提交
     * @throws IllegalArgumentException  目标成员列表为空或与当前相同
     */
    public synchronized void proposeChange(String changeId, Set<String> newMembers) {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(newMembers, "newMembers");
        if (config.isJoint()) {
            throw new ChangeInProgressException(
                    "change '" + lastChangeId + "' is still in joint phase at version " + config.version());
        }
        if (changeId.equals(lastChangeId)) {
            throw new DuplicateChangeException("change '" + changeId + "' was already submitted");
        }
        if (newMembers.equals(config.members())) {
            throw new IllegalArgumentException("new members must differ from current members");
        }
        this.config = ClusterConfiguration.joint(config.members(), newMembers, config.version() + 1);
        this.jointStartMillis = clock.millis();
        this.jointDecisions = 0;
        this.lastChangeId = changeId;
        persist();
    }

    /**
     * 尝试通过一项决议。联合阶段要求投票同时构成新旧两份配置的多数派；
     * 联合阶段中每通过一次决议，统计计数 +1。
     *
     * @return 决议是否被接受
     */
    public synchronized boolean decide(Set<String> voters) {
        boolean accepted = config.isQuorum(voters);
        if (accepted && config.isJoint()) {
            jointDecisions++;
            persist();
        }
        return accepted;
    }

    /**
     * 提交在途变更：要求联合多数派确认，随后新配置成为唯一稳定配置。
     *
     * @throws NoChangeInProgressException 当前没有变更在联合阶段
     * @throws MembershipChangeException   投票未构成联合多数派
     */
    public synchronized void commitChange(Set<String> voters) {
        if (!config.isJoint()) {
            throw new NoChangeInProgressException("no membership change is in progress");
        }
        if (!config.isQuorum(voters)) {
            throw new MembershipChangeException(
                    "commit requires a joint majority of both old and new configurations");
        }
        long duration = clock.millis() - jointStartMillis;
        this.config = ClusterConfiguration.stable(config.newMembers(), config.version());
        this.lastChangeMillis = duration;
        persist();
    }

    /** 当前配置（联合期包含新旧两份成员列表）。 */
    public synchronized ClusterConfiguration configuration() {
        return config;
    }

    /** 当前统计快照。 */
    public synchronized MembershipMetrics metrics() {
        long duration = config.isJoint() ? clock.millis() - jointStartMillis : lastChangeMillis;
        return new MembershipMetrics(
                config.version(),
                config.isJoint(),
                config.oldMembers().size(),
                config.newMembers().size(),
                jointDecisions,
                duration);
    }

    private void persist() {
        store.save(new MembershipState(
                config.isJoint(),
                config.version(),
                config.oldMembers(),
                config.newMembers(),
                jointStartMillis,
                jointDecisions,
                lastChangeId,
                lastChangeMillis));
    }
}
