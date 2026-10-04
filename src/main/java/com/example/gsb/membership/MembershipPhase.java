package com.example.gsb.membership;

/** 成员变更所处的阶段。 */
public enum MembershipPhase {
    /** 稳定期：只有一份生效配置。 */
    STABLE,
    /** 联合共识期：新旧两份配置并存，决议需同时满足两边多数派。 */
    JOINT
}
