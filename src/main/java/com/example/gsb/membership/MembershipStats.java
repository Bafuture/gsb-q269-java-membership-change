package com.example.gsb.membership;

/**
 * 成员变更统计信息。
 *
 * @param currentVersion           当前配置版本号
 * @param phase                    当前阶段
 * @param oldConfigSize            旧（当前）配置成员数
 * @param newConfigSize            新配置成员数；稳定期为 0
 * @param jointDecisions           本次联合阶段已达成的决议次数
 * @param changesCompleted         已完成的变更次数
 * @param lastChangeDurationMillis 上一次变更耗时（毫秒）；从未完成过变更时为 null
 */
public record MembershipStats(
        long currentVersion,
        MembershipPhase phase,
        int oldConfigSize,
        int newConfigSize,
        long jointDecisions,
        long changesCompleted,
        Long lastChangeDurationMillis) {
}
