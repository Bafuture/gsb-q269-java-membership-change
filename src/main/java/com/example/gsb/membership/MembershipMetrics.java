package com.example.gsb.membership;

/**
 * 成员变更统计快照。
 *
 * @param currentVersion       当前配置版本号（联合期为目标配置版本）
 * @param jointInProgress      是否处于联合共识阶段
 * @param oldConfigSize        旧配置成员数
 * @param newConfigSize        新配置成员数（稳定期与旧配置相同）
 * @param jointDecisions       最近一次联合阶段中通过的决议数
 * @param changeDurationMillis 变更耗时：联合期进行时为已耗时，稳定期为最近一次变更的总耗时
 */
public record MembershipMetrics(
        long currentVersion,
        boolean jointInProgress,
        int oldConfigSize,
        int newConfigSize,
        long jointDecisions,
        long changeDurationMillis) {
}
