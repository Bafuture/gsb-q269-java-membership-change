package com.example.gsb.membership;

import java.time.Clock;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 集群成员变更组件，实现简化的联合共识（joint consensus）流程：
 *
 * <ol>
 *   <li>{@link #proposeChange(Set)}：从稳定期进入联合期，新旧配置并存，版本号 +1；</li>
 *   <li>联合期内 {@link #decide(Collection)} 要求投票同时构成新旧两份配置的多数派；</li>
 *   <li>{@link #commit()}：新配置成为唯一生效配置，变更完成。</li>
 * </ol>
 *
 * <p>每次状态迁移都会先持久化到 {@link MembershipStore}，因此节点崩溃重启后
 * 会按当前配置版本继续，而不会回退到旧配置。线程安全。
 */
public final class MembershipManager {

    private final MembershipStore store;
    private final Clock clock;
    private MembershipState state;

    /** 从已有存储恢复（重启场景）。存储为空时请使用 {@link #bootstrap}。 */
    public MembershipManager(MembershipStore store, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.state = store.load()
                .orElseThrow(() -> new IllegalStateException(
                        "membership store is empty; bootstrap the cluster first"));
    }

    public MembershipManager(MembershipStore store) {
        this(store, Clock.systemUTC());
    }

    /** 初始化一个全新的集群（版本 1 的稳定配置）。 */
    public static MembershipManager bootstrap(MembershipStore store, Clock clock, Set<NodeId> members) {
        Objects.requireNonNull(store);
        if (store.load().isPresent()) {
            throw new IllegalStateException("membership store already initialized");
        }
        MembershipState initial = MembershipState.initial(members);
        store.save(initial);
        return new MembershipManager(store, clock);
    }

    public static MembershipManager bootstrap(MembershipStore store, Set<NodeId> members) {
        return bootstrap(store, Clock.systemUTC(), members);
    }

    /**
     * 提交一次成员变更，进入联合共识阶段。
     *
     * @throws ChangeInProgressException 已有变更正在进行（一次只允许一个变更）
     * @throws DuplicateChangeException  目标成员列表与当前生效配置完全一致
     * @throws InvalidConfigurationException 目标成员列表为空
     */
    public synchronized MembershipState proposeChange(Set<NodeId> newMembers) {
        Objects.requireNonNull(newMembers, "newMembers must not be null");
        if (state.isJoint()) {
            throw new ChangeInProgressException(
                    "a membership change to version " + state.version() + " is already in progress");
        }
        if (newMembers.isEmpty()) {
            throw new InvalidConfigurationException("new member list must not be empty");
        }
        if (newMembers.equals(state.oldMembers())) {
            throw new DuplicateChangeException(
                    "proposed member list is identical to the current configuration");
        }
        state = new MembershipState(
                state.version() + 1,
                MembershipPhase.JOINT,
                state.oldMembers(),
                newMembers,
                0,
                clock.millis(),
                state.changesCompleted(),
                state.lastChangeDurationMillis());
        store.save(state);
        return state;
    }

    /**
     * 尝试达成一次决议。稳定期需当前配置多数派；联合期需新旧两份配置
     * 各自的多数派同时满足，否则拒绝，保证不会出现两个多数派各自决策。
     *
     * @return 决议是否被接受
     */
    public synchronized boolean decide(Collection<NodeId> votes) {
        boolean accepted = state.accepts(votes);
        if (accepted && state.isJoint()) {
            state = new MembershipState(
                    state.version(),
                    state.phase(),
                    state.oldMembers(),
                    state.newMembers(),
                    state.jointDecisions() + 1,
                    state.changeStartMillis(),
                    state.changesCompleted(),
                    state.lastChangeDurationMillis());
            store.save(state);
        }
        return accepted;
    }

    /**
     * 提交变更：新配置成为唯一生效配置，回到稳定期。
     *
     * @throws NoChangeInProgressException 当前没有进行中的变更
     */
    public synchronized MembershipState commit() {
        if (!state.isJoint()) {
            throw new NoChangeInProgressException("no membership change in progress");
        }
        long duration = Math.max(0, clock.millis() - state.changeStartMillis());
        state = new MembershipState(
                state.version(),
                MembershipPhase.STABLE,
                state.newMembers(),
                Set.of(),
                0,
                0,
                state.changesCompleted() + 1,
                duration);
        store.save(state);
        return state;
    }

    /** 当前状态快照（不可变）。 */
    public synchronized MembershipState state() {
        return state;
    }

    /** 当前统计信息。 */
    public synchronized MembershipStats stats() {
        return new MembershipStats(
                state.version(),
                state.phase(),
                state.oldMembers().size(),
                state.newMembers().size(),
                state.jointDecisions(),
                state.changesCompleted(),
                state.changesCompleted() > 0 ? state.lastChangeDurationMillis() : null);
    }
}
