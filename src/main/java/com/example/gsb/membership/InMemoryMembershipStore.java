package com.example.gsb.membership;

import java.util.Optional;

/**
 * 进程内持久化存储。它本身不会跨进程存活，但其生命周期独立于 manager
 * —— 崩溃恢复测试中可以丢弃 manager 再用同一个 store 重建，模拟重启。
 */
public final class InMemoryMembershipStore implements MembershipStore {

    private volatile MembershipState state;

    @Override
    public Optional<MembershipState> load() {
        return Optional.ofNullable(state);
    }

    @Override
    public synchronized void save(MembershipState newState) {
        this.state = newState;
    }
}
