# 集群成员变更组件（联合共识）

基于简化联合共识（Joint Consensus）的运行时集群成员变更组件，保证变更期间
不会出现新旧两个多数派各自决策（脑裂）。

## 运行方式

```bash
./mvnw -q verify   # 或 mvn -q verify
```

## 功能概览

- **成员变更**：支持节点加入与移除，每次变更使配置版本号 +1（`MembershipManager.proposeChange`）。
- **联合共识**：变更进入联合阶段后，新旧两份成员配置并存；任何决议（`decide`）
  与变更提交（`commitChange`）都必须**同时**满足旧配置多数派和新配置多数派。
- **安全性**：联合规则保证任意两个合法法定人数必然相交，冲突决议无法同时通过，
  由 `JointConsensusSafetyTest` 以场景 + 穷举方式证明。
- **中断恢复**：每次状态转换先持久化（`MembershipStore`）再生效；节点崩溃重启后
  用 `MembershipManager.recover` 按当前配置版本继续，绝不回退到旧配置。
- **单变更约束**：同一时刻只允许一个变更；在途变更期间的新提交抛
  `ChangeInProgressException`，相同 changeId 的重复提交抛 `DuplicateChangeException`。
- **统计**：`metrics()` 返回当前配置版本、新旧配置成员数、联合阶段通过的决议数、
  变更耗时（进行中为已耗时，完成后为总耗时）。

## 快速上手

```java
InMemoryMembershipStore store = new InMemoryMembershipStore(); // 生产环境换成带 fsync 的实现
MembershipManager manager = MembershipManager.bootstrap(store, Clock.systemUTC(), Set.of("A", "B", "C"));

// 1. 提交变更：{A,B,C} -> {A,B,C,D}，进入联合阶段，版本 0 -> 1
manager.proposeChange("add-D", Set.of("A", "B", "C", "D"));

// 2. 联合阶段：决议需同时满足新旧配置多数派
boolean ok = manager.decide(Set.of("A", "B", "C", "D"));

// 3. 联合多数派确认后提交，新配置成为唯一稳定配置
manager.commitChange(Set.of("A", "B", "C", "D"));

// 崩溃恢复：丢弃进程内状态，从持久化存储重建，按当前版本继续
MembershipManager resumed = MembershipManager.recover(store, Clock.systemUTC());
```

## 代码结构

| 类 | 职责 |
|----|------|
| `ClusterConfiguration` | 不可变配置：稳定（单份成员）或联合（新旧两份成员），提供 `isQuorum` 判定 |
| `MembershipManager` | 变更生命周期：propose → 联合决议 → commit；持久化与恢复；统计 |
| `MembershipState` / `MembershipStore` | 可持久化快照与存储接口（`InMemoryMembershipStore` 为内存实现） |
| `MembershipMetrics` | 统计快照：版本、新旧成员数、联合决议数、变更耗时 |
| `ChangeInProgressException` / `DuplicateChangeException` / `NoChangeInProgressException` | 变更拒绝语义 |

## 为什么不能一次性替换整个配置

假设直接把配置从 `{A,B,C}` 原子切换为 `{D,E,F}`。切换瞬间无法保证所有节点在同一
时刻看到新配置：一部分节点仍按旧配置运行，另一部分已按新配置运行。此时：

- 旧配置中 `{A,B}` 是多数派（2/3），可以独立通过决议；
- 新配置中 `{D,E}` 是多数派（2/3），也可以独立通过决议；
- 两组节点**互不相交**，于是两个多数派对同一议题作出冲突决议 —— 脑裂。

`JointConsensusSafetyTest.atomicReplacementWouldAllowDisjointMajorities` 用代码
复现了这一风险。

## 推荐做法

1. **联合共识（本组件采用）**：变更分两段——先进入新旧配置并存的联合阶段，
   期间所有决议要求双重多数派；联合多数派确认后再切换到新配置。任意两个合法
   法定人数必然相交，从机制上杜绝脑裂。
2. **单节点变更（Raft 风格）**：更简化的替代方案——每次只增删一个节点，
   相邻配置的多数派必然相交，无需联合阶段；代价是变更粒度细、耗时长。
3. **工程纪律**：无论哪种方案，都应做到——同一时刻只进行一个变更、变更标识
   幂等去重、状态转换先持久化再生效、崩溃后按最新持久化版本恢复。
