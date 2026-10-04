# 集群成员变更组件（联合共识）

在运行中安全地增删集群节点的 Java 组件。核心思想是**联合共识（joint consensus）**：
成员变更期间新旧两份配置并存，任何决议必须同时满足两份配置各自的多数派，
从而保证任何两个合法多数派必然相交，杜绝“两个多数派各自决策”的脑裂。

## 构建与测试

```bash
./mvnw -q verify    # 或 mvn -q verify
```

## 组件结构（`com.example.gsb.membership`）

| 类 | 职责 |
|----|------|
| `MembershipManager` | 变更流程入口：`proposeChange` → 联合期 `decide` → `commit` |
| `MembershipState` | 可持久化的完整状态快照（版本、阶段、新旧成员、统计） |
| `ClusterConfiguration` | 带版本号的成员配置，提供多数派判定 |
| `MembershipStore` | 持久化抽象；实现：`InMemoryMembershipStore`、`FileMembershipStore`（原子落盘） |
| `MembershipStats` | 统计：当前版本、新旧配置成员数、联合阶段决议次数、变更耗时 |

## 变更流程

1. **提交变更** `proposeChange(newMembers)`：版本号 +1，进入联合期，状态先持久化再生效。
   一次只允许一个变更；进行中再次提交抛 `ChangeInProgressException`，
   目标与当前配置一致抛 `DuplicateChangeException`。
2. **联合期决议** `decide(votes)`：投票必须同时构成旧配置和新配置的多数派才被接受，
   并计入联合阶段决议次数。
3. **提交完成** `commit()`：新配置成为唯一生效配置，记录变更耗时，回到稳定期。
4. **崩溃恢复**：每次状态迁移（进入联合期、联合期决议、提交完成）都先落盘，
   重启后用同一 `MembershipStore` 重建 `MembershipManager`，按当前配置版本继续，
   不会回退到旧配置。

```java
var store = new FileMembershipStore(Path.of("/var/lib/node/membership.properties"));
var manager = MembershipManager.bootstrap(store, Set.of(NodeId.of("A"), NodeId.of("B"), NodeId.of("C")));

manager.proposeChange(Set.of(NodeId.of("A"), NodeId.of("B"), NodeId.of("C"), NodeId.of("D"))); // v2 联合期
boolean ok = manager.decide(votes);   // 需同时满足 {A,B,C} 与 {A,B,C,D} 的多数派
manager.commit();                     // v2 稳定期
MembershipStats stats = manager.stats();
```

## 为什么不要一次性替换整个配置

假设直接把 {A,B,C} 整体切换为 {D,E,F}：切换瞬间无法保证所有节点同时生效，
{A,B} 仍按旧配置构成多数派、{D,E} 已按新配置构成多数派——两组投票**互不相交**，
却都能各自通过决议，于是同一时刻出现两个冲突的“合法多数派”（脑裂），
数据可能产生不可调和的分叉。本组件的
`JointConsensusSafetyTest.oneShotReplacementWouldAllowTwoDisjointMajorities`
用测试复现了这一风险。

## 推荐做法

- **生产环境**：采用联合共识（本组件实现的流程），新旧配置并存期间决议需双重多数派；
  任何两个联合多数派必然相交，冲突决议在数学上不可能发生
  （见 `JointConsensusSafetyTest.anyTwoAcceptedVoteSetsIntersect` 的穷举验证）。
- **变更幅度**：优先**单节点步进**（每次只增/删一个节点），把联合期的双重多数派约束
  降到最低，也便于定位问题；避免整组替换。
- **一次一个变更**：上一次变更 commit 完成前不发起新变更（组件已强制）。
- **先持久化后生效**：配置版本与联合状态先落盘再对外生效，崩溃恢复后按当前版本继续。
