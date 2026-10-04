package com.example.gsb.membership;

import java.util.Optional;

/** 成员状态的持久化抽象，保证重启后可以按当前配置版本继续。 */
public interface MembershipStore {

    /** 读取最近一次持久化的状态；从未初始化时返回 empty。 */
    Optional<MembershipState> load();

    /** 原子地持久化最新状态。 */
    void save(MembershipState state);
}
