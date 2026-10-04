package com.example.gsb.membership;

/**
 * 内存实现的 {@link MembershipStore}。
 *
 * <p>语义上模拟“同步刷盘后立即对同机重启可见”：状态以 {@code volatile} 引用保存。
 * 生产环境应换成带 fsync 的 WAL / 文件实现，测试中用它模拟崩溃后从同一存储重建管理器。</p>
 */
public final class InMemoryMembershipStore implements MembershipStore {

    private volatile MembershipState state;

    @Override
    public synchronized void save(MembershipState state) {
        this.state = state;
    }

    @Override
    public synchronized MembershipState load() {
        return state;
    }
}
