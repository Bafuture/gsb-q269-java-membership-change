package com.example.gsb.membership;

/**
 * 成员状态的持久化存储。实现需保证 {@link #save(MembershipState)} 在返回前完成落盘
 * （fsync / 强制刷盘），否则崩溃恢复无法保证状态不回退。
 */
public interface MembershipStore {

    /**
     * 原子持久化一份状态快照。返回后，即使进程崩溃，{@link #load()} 也必须能读到该快照。
     */
    void save(MembershipState state);

    /**
     * 读取最近一次持久化的快照。从未持久化过时返回 {@code null}。
     */
    MembershipState load();
}
