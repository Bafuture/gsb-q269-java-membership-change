package com.example.gsb.membership;

import java.util.Set;

/**
 * 可持久化的成员管理状态快照。每次状态转换都会持久化一份新快照，
 * 使节点崩溃重启后能按当前配置版本继续，而不是回到旧配置。
 *
 * @param joint            是否处于联合共识阶段
 * @param version          当前（联合期为目标）配置版本号
 * @param oldMembers       旧配置成员
 * @param newMembers       新配置成员（稳定期与旧配置相同）
 * @param jointStartMillis 本次联合阶段开始的时钟值，未进入过联合阶段时为 0
 * @param jointDecisions   最近一次联合阶段中通过的决议数
 * @param lastChangeId     在途或最近一次完成的变更标识，用于识别重复提交
 * @param lastChangeMillis 最近一次完成的变更耗时，单位毫秒
 */
public record MembershipState(
        boolean joint,
        long version,
        Set<String> oldMembers,
        Set<String> newMembers,
        long jointStartMillis,
        long jointDecisions,
        String lastChangeId,
        long lastChangeMillis) {
}
