# 航空箱阶段一交接

更新时间：2026-08-10

## 第二阶段已确认规则

- 拆分后的记录只允许在本次拆分产生的新结构之间重分配；不能自动搬到远处旧结构。
- 若没有任何新结构能容纳该生物的最小单体包络，优先在安全位置释放真实实体；找不到安全位置或实体类型未知时，掉落带 `creature_record` DataComponent 的生物物品。
- 收纳工具采用“托举状态”思路，未来通过自身接口和可选搬运模组桥接；右键舱门直接提交同一条记录转移事务。
- 村民仅收容时可用 `1x1x2`；交易村民独占一个 `3x3x3` 站位，并需要床、工作方块和置物台三个固定装置。
- 航空箱本身不需要动力。饲料通过 ItemHandler 输入，交易/采集由外部传送带、机械手和动力臂完成。
- 航空学组装选中任何航空箱成员时，必须原子包含整个逻辑结构；航空箱可以和普通方块共同组装。

## 第二阶段当前实现

- `CreatureRecord`：唯一 UUID、实体类型、profile、幼年状态、年龄、生命和 revision；物品记录使用持久化 DataComponent。
- `CreatureProfile`/`CreatureProfiles`：默认牛、羊、猪、鸡、村民包络；成年包络和幼体成长预留使用整数体素规则。
- `CreatureCapacitySolver`：支持成年包络、水平旋转、爆满保留；单只都放不下才进入释放候选。
- `AviationCrateStructureFinder`：对剩余方块集合按最大体积、最小表面积和坐标稳定拆分为合法 `3x5x32` 范围内长方体。
- `CrateSplitAllocator`：只在本次拆分结果中分配，无法单体容纳的记录进入实体释放候选。
- `AviationCrateBlockEntity`：记录列表、繁殖模式、饲料输入缓冲、产物输出缓冲、交易固定装置与持久化状态；玩家配置只保留繁殖模式，不暴露繁殖/交易速度。
- `CreatureReleaseService`：优先安全生成实体；无法安全生成时掉落 `sealed_creature` 记录物品。
- `TradingStationRules`/`TradingProcessor`：每名交易村民必须占用独立 `3x3x3` 站位及床、工作方块、置物台；直接手持床、职业工作站和讲台右键航空箱即可存入固定槽。报价输入由输入缓冲原子消费，结果仅在输出缓冲有空间时写入，并持久化报价使用次数。该实现不引用或依赖 Trading Floor。
- 安山和黄铜两格卷帘舱门：只允许挂在高度至少两格的航空箱外边界；开放后自动收纳进入外侧开口、且高度不超过两格的动物和村民。舱门是标准 `ItemHandler` 自动化端口；黄铜门可放入一条生物记录作为过滤样本，潜行空手右键取回样本。
- Create 风格模式值框：右键航空箱打开透明世界内面板，松开右键切换繁殖模式；没有繁殖速度或交易速度选项。
- 生物固定器：放在传送带旁以收纳被推入范围的实体，输出带 Data Component 的生物记录，供传送带、机械手、动力臂和黄铜舱门过滤使用；记录 Shift+右键可安全释放实体。
- 独立 `aircrate:main` 创造模式标签页：以航空箱为图标，展示结构方块、舱门、生物固定器、生物收纳器和饲料。生物记录和旧的占位固定装置不在创造栏，前者只由固定器或安全兜底产生。
- Sable/Aeronautics 可选 Mixin：组装选区包含任意航空箱时必须包含其完整逻辑结构；移动结束后以变换后坐标一次性重建每个结构。

仍需在完整整合包中手动验证 Create 机械臂路径、Carry On 客户端同步和 Aeronautics 实际组装流程；不启动 Minecraft、不安装 JAR。

## 已完成

- 已读取根工程 `build.gradle`、`gradle.properties`、`README.md`、`docs/TOXIC_FOG_MECHANICS_STATUS_2026-08-04.md` 和 `docs/PROJECT_HANDOFF_2026-07-31.md`。
- 已确认本模组独立于 `createsurvival`；运行环境为 NeoForge `21.1.219`、Minecraft `1.21.1`、Java 21，本地存在 Create 6.0.10、Ponder 1.0.82、Curios 9.5.1 jar。
- 已确认根工程 Git 无提交且有大量未跟踪文件；本阶段没有删除、重置或覆盖根工程文件。
- 已建立独立目录 `air-crate/`、mod id `aircrate`、包名 `com.llqqrr.aircrate`，并添加最小 NeoForge 入口、唯一航空箱方块注册、元数据模板和语言资源。
- 已将多方块边界、体素容量/成长预留、分裂重组、记录 ID/journal、舱口、插件、Create ItemHandler、兼容层、避难所生成和交易网络写入 `docs/AIR_CRATE_DESIGN.md`。

## 依赖和运行方式

基座运行时依赖 Minecraft/NeoForge/Create；Ponder、Curios、Carry On、Sable、Aeronautics 和 Jade 是 optional。`createsurvival` 没有依赖声明，也没有代码引用。Create API 仅以 compile-only 方式参与构建，不会被打包；Sable 组装适配使用目标名与反射，不会把任何外部模组类打包进产物；Trading Floor 没有依赖声明，也没有代码引用。

构建应在 ASCII 映射盘符中执行：

```powershell
Set-Location <ascii-path>\air-crate
..\gradlew.bat --no-daemon --gradle-user-home .gradle-home-aircrate clean test build
```

本阶段不启动 Minecraft、不安装 jar、不修改测试整合包。

## 未决讨论

- `CreatureProfile` 的默认体素单位、旋转集合、group rule 和数据包 schema 需要先定一版规范，再实现注册表。
- 结构分裂候选的“尽可能大”定义已给出确定性 tie-break，但仍需用更多不规则破坏形状做 GameTest。
- 无法容纳记录时默认使用封存工具；是否允许自动转移到邻近结构、转移半径和玩家提示需要确认。
- 空气/动力/过滤器的基座数值，以及 Create:Survival Toxic Fog API 的公开适配接口尚未确定。
- 传送带固定器、生物漏斗和包裹物流需要先确定记录物品 DataComponent 的上限和安全标签。
- 避难所只对新区块生成，已生成村庄默认不动；迁移命令是否需要及其权限范围待讨论。
- 交易订单、费用、天气/毒雾风险、保险和地面村民最低服务覆盖只完成数据模型，禁止直接实现最终经济系统。
- FTB Unearthed 仅作玩法参考。由于本地没有其 jar/source，且未获得可复用代码/资源许可，下一阶段必须补充官方机制和许可证来源记录。

## 构建验证

- 在仓库根目录执行 `clean test build` 已通过。
- 单元测试覆盖牛的容量/爆满/分裂释放候选，以及交易村民 `3x3x3` 站位约束。
- Mixin 可选依赖探测仅查询 `.class` 资源，不得使用 `Class.forName` 提前加载 Sable 的 `SubLevelAssemblyHelper`；否则会使 Drive By Wire 等后续 Mixin 报 `MixinTargetAlreadyLoadedException`。
- 最终 `aircrate-0.1.0.jar` 未包含 Create、Sable、Carry On 或 Trading Floor 的类。

## 下一轮实机验证

- 使用 Create 机械手、动力臂、漏斗和溜槽分别验证输入只进饲料槽、输出只从产物槽提取。
- 使用 Carry On 托举生物，在关闭和打开的舱门上分别验证不会丢失或复制记录。
- 使用 Create Aeronautics 选择航空箱的一部分、全部及与普通方块混合的选区，验证拒绝/通过和移动后的重组。
- 在交易输出满、报价耗尽、输入不足和多个交易村民的情况下验证交易停止且不吞物。

## 2026-09-22 收尾轮（Codex 接手）

- 完成并构建 Kimi 配额耗尽前留下的航空箱修复：结构按 `PLACEMENT_FACING` 分组、逻辑长轴固定为放置朝向、箱内生物按世界轴映射并居中/缩放。
- 修复包裹世界渲染顺序：先提交包裹内实体，再提交外壳，避免掉落物 GROUND/FIXED 渲染时外壳深度遮挡观察窗内生物；未知/损坏记录仍保留外壳并输出一次诊断日志。
- `test build` 已通过；产物 `aircrate-0.1.0.jar` 已部署到 AOZ1.3.4 内部联机测试 `mods` 目录。需要重启游戏后验证：掉落包裹可见生物、不同朝向箱子不合并、长箱横纵方向与箱内生物居中。
- 额外检查发现 AOZ 日志存在 Weather2 与 CoroUtil 的 `NoSuchMethodError` 刷屏，属于整合包依赖版本不匹配，与本模组无关，暂未修改。

## 2026-09-17 修复轮（由 Kimi Code 接手完成）

前置备份：`backups/aircrate-0.1.0-pre-bugfix-round1-20260917-050419.jar` + 同名 `-source.zip`。

### 用户报告的三个 bug（根因与修复）

- **伸出单格漏物品**：根因不是结构识别（长方体校验本身正确），而是 `AviationCrateWorldManager.rebuildAt` 把放置也当拆除事务，`distributeItems` 轮询均分物品到所有分区。修复：放置路径做几何 diff，未变分区不搬物品/生物、不换 structureId；分配改为优先回到原 controller 分区、按体积降序溢出、插不下在原 controller 处掉落。
- **溜槽贴侧面乱抽**：`ACBlockEntities` 的 `AVIATION_CRATE` 能力回调忽略方向。修复：仅 `OUTPUT_FACING` 且 `OUTPUT_OPEN` 的面暴露库存，其余面返回 null。**饲料槽保持可抽取（用户确认是有意设计）**。注意物流舱门（hatch）的能力仍是六面暴露，因舱门本就是专用物流端口，维持现状。
- **机械手挤奶吞桶**：`CrateInteractionHatchBlock` 整组替换主手物品。修复：`shrink(1)` + `ItemHandlerHelper.giveItemToPlayer`。

### 严重档修复

- **S1 封存生物客户端不可见**：`ACDataComponents` 三个组件补 `.networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(...))`。
- **S2 蛙港过滤器复制**：放置消耗物品（创造除外）；覆盖放置时旧过滤器返还玩家。
- **S3 战利品表**：补齐 aviation_crate、andesite/brass_crate_hatch、crate_interaction_hatch、creature_belt_fixture 共 5 个（其余 fixture/进气扇是纯物品，无需表）。
- **S4 共振器每 tick 扫 3 万格**：蛙港缓存绑定共振器（isLoaded 守卫 + 失效才重搜 + 无结果冷却 20t）；共振器自身扫描只扫已加载区块，countBoundFrogports 20→40t、emitWaves 40→80t。
- **S5 全箱 NBT 网络风暴**：`changed()` 只置脏，`tickServer` 最多每 20t 冲刷一次；`getUpdateTag` 收窄为结构/模式字段，不再下发记录与库存。代价：模式开关等状态客户端最多滞后 1s。

### 高危档修复

- **H1** 锯盘击杀包裹后只清输入槽，不再 `clear()` 整个库存。
- **H3** 挤奶/剪毛：逐只冷却 6000t（幼体 3000t 恢复羊毛），跳过幼体，支持牛/哞菇/山羊；羊毛颜色读 entityData `Color`；`CreatureRecord` 新增 `lastMilkGameTime`/`sheared`/`lastShearedGameTime`（codec 向后兼容），BE 的 saveAdditional/loadAdditional 已同步持久化这三个字段。
- **H4** 打包机先确认 `removeRecord` 成功再消费订单。
- **H5** 蛙港捕获：插入成功后、动画前先 discard 实体，消除复制窗口。
- **H6** 捕获 journal：新增 `CreatureCaptureJournal`（SavedData，PREPARE→DESTINATION_COMMITTED→SOURCE_TOMBSTONED→COMPLETE），ServerStartedEvent 重放未完成事务，记录缺失时补回或掉落封存物品。局限：受 autosave 窗口限制，非真·写前日志。
- **H7** 释放清除 Motion/Rotation/FallDistance/Fire，客户端 displayEntity 同步对齐。
- **H8** `controller()`/`automationInventory()` 加 `isLoaded(controllerPos)` 守卫，不再强加载控制器区块。
- **H9** 蛙港界面网络包补 `mayBuild` 校验（服务器防熊，单人无影响）。

### 明确未做（用户决定）

- H2（饲料槽可抽）是有意设计，本文档"下一轮实机验证"第一条的旧假设作废。
- H10 死代码（CarryOn 桥、tryCaptureAtHatch、风扇过滤器、PackagerStockMixin）保留，可能是未实现功能的前置。
- 中低优先级问题（创造栏缺方块、配方稀缺、交易快照丢 priceMultiplier、UUID 解码异常、entityData 无上限等）留待下轮。
- 交互方式改造（护理站方块）、机械臂（Arm）兼容、材质模型——待用户决策。机械手性能分析见 `docs/DEPLOYER_PERFORMANCE_NOTES.md`。
