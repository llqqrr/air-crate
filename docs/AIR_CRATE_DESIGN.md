# 航空箱设计基线

状态：第二阶段第一版基线；经济系统、避难所生成和外部模组适配仍未最终定稿。

## 1. 边界和目标

航空箱是独立模组 `aircrate`，不是实体生物笼。箱内不长期运行真实生物实体；服务器保存可校验的生物记录，只有在明确的释放、交易或工具交互时才生成实体。基础模组提供方块、多方块状态、记录存储、容量算法、舱口、记录工具、插件接口和基础生命支持。Create:Survival 的毒雾实现不复制到本项目。

基础体验必须在没有 Sable 或其他可选模组时可启动；项目不依赖也不声明 `createsurvival`。兼容层只能通过公开接口和事件接入，不能在基座类加载时直接引用可选模组类。

## 2. 工程身份和依赖

| 项目 | 决定 |
| --- | --- |
| 目录 | 仓库根目录 `air-crate` |
| mod id | `aircrate` |
| 包名 | `com.llqqrr.aircrate` |
| 方块 | `aircrate:aviation_crate`，唯一普通正方体方块 |
| 运行底座 | Minecraft 1.21.1、NeoForge 21.1.219+、Java 21 |
| 必需依赖 | 仅 Minecraft/NeoForge |
| 可选依赖 | Ponder 1.0.82、Curios 9.5.1、Carry On、Jade、Sable、Aeronautics |

`build.gradle` 只把本地 Create/Ponder/Curios jar 声明为 compile-only，绝不打包；Create 由模组元数据声明为运行时必需依赖。可选兼容实现使用独立的 Gradle source set；基座不得引用可选模组的包名。

## 3. 方块和多方块结构

航空箱由同一种方块自由拼成轴对齐长方体。合法尺寸为：高度 `1..3`、宽度 `1..5`、长度 `1..32`，因此最大为 `3x5x32`，也支持 `1x1`、`1x1x2`、`2x2x1`、`2x3x1` 等退化形状。玻璃模型和边框材质必须按相邻面拼接，组合后看起来是连续玻璃箱，而不是一堆互相遮挡的独立方块。

每个结构只有一个逻辑控制器：控制器坐标为结构包围盒的最小 `(x,y,z)`，方向和尺寸写入 `StructureState`。其他方块只保存控制器引用和结构版本；引用失效时不得各自创建控制器。扫描、加载、破坏和重组都在服务器线程完成，并用结构版本号防止旧网络包覆盖新状态。

结构判定步骤：

1. 以被交互方块为种子，沿六邻接收集同类航空箱方块，限制在 5x3x32 的窗口内。
2. 求轴对齐包围盒，检查盒内每个位置是否存在航空箱方块；不完整的盒子进入分裂算法，而不是被强行当成一个结构。
3. 为合法盒子选出最大体积、再最大最小边、再最小控制器坐标的确定性结果。
4. 记录、插件、缓存和控制器迁移在一次可回滚事务中提交。

## 4. 结构化生物记录

记录不是任意嵌套 NBT，也不能携带实体 UUID 继续在世界中运行。建议使用版本化 Data Component `aircrate:creature_record`，字段如下：

```text
schema: 1
record_id: UUID                 // 全局唯一、不可由客户端指定
entity_type: ResourceLocation   // 例如 minecraft:cow
profile_id: ResourceLocation    // 容量/交互规则的注册表键
variant: canonical component map
age_ticks: long
health: fixed-point value
genes: bounded component map
inventory: bounded list of item stacks
custom_name: optional text
source: {world, dimension, position, owner}
revision: long
```

`variant`、`genes` 和 `inventory` 只能接受注册过的字段、长度和字节上限；未知字段保留为不透明的有界 blob，不能递归保存完整实体 NBT。每条记录 ID 在 `RecordIndexSavedData` 中登记 owner、当前容器、revision、状态（`STORED`、`IN_TRANSIT`、`SEALED`、`RELEASED`、`TOMBSTONE`）。物品只携带记录快照和 revision；移动时先写目的地，再用 journal 将来源标记为 tombstone，重放同一事务不会复制。

可持久化的结构状态至少包括：控制器、盒子尺寸、结构版本、记录 ID 列表、插件实例及版本、输出缓存、空气/动力状态、爆满原因、最后一次事务 ID。区块卸载前不生成临时实体；加载时校验索引并修复半提交事务。

## 5. 可注册容量算法

### 5.1 Profile

`CreatureProfile` 由代码注册或数据包扩展，至少声明：

```text
adult_size: width, height, length       // 整数格包络
allowed_rotations: enum set             // 默认只允许水平旋转
adult_volume: fixed-point                // 默认 size 乘积，可覆盖
juvenile_ratio: fixed-point              // 实际占用比例
growth_envelope: adult_size              // 幼体最终成长必须预留
group_rule: adjacent | column_stack | custom
max_group_density: optional fixed-point
```

尺寸是“可安全容纳的包络”，不从运行中的实体碰撞箱临时推断。方向限制、群体堆叠和自定义 profile 都必须能在 GameTest 中复现。

### 5.2 放置和成长

容量求解把结构内部空间转为整数格体素。对每个成年记录枚举允许方向，用确定性 first-fit decreasing 搜索不重叠包络；tie-break 为剩余空间最大、旋转序号最小、坐标字典序。成年生物的包络必须完整位于结构内部，边界外一格也视为失败。

幼体可以用 `juvenile_ratio` 缩小实际占用并填充空隙，但同时锁定一个不可被其他成长预留占用的 `growth_envelope`。因此“幼体能塞进去”不等于“可以再产生幼体”：若第二个幼体没有完整的成年预留空间，繁育处理器必须停止。取出幼体或扩大结构后才释放预留。

验收例：成年牛 profile 为 `1x2x2`，只允许水平旋转。`1x2x2` 可容纳一只，`2x2x2` 可并排容纳两只；第三只只能在结构扩大后进入。爆满只表示“至少有一只安全容纳但没有可用交互余量”，不是允许塞入实体。

### 5.3 数据包示例

后续可提供 `data/<namespace>/aircrate/creature_profiles/cow.json`，而不是为每种生物硬编码 Java。数据包必须通过 schema 校验尺寸上限、比例范围和未知 group rule，并在加载失败时拒绝该 profile 而不是猜测默认值。

## 6. 破坏、分裂和无损降级

破坏方块后，剩余方块先按六邻接分量分组，再枚举每个分量中的合法长方体。候选按体积降序、表面积升序、控制器坐标字典序选择，直到所有方块被覆盖；不能被长方体覆盖的单方块仍是合法 `1x1x1` 结构。候选选择和 tie-break 固定后，服务器重启前后结果一致。

记录分配采用“先保留成年、再保留有成长预留的幼体、最后按 record_id 字典序”的稳定顺序。每个候选结构独立运行容量求解：能容纳的记录迁入；不能容纳的记录进入附近结构的候选队列。例：`2x5x2` 中五只牛，移除中间长边方块后，两个 `2x2x2` 各收两只/三只，狭小结构不接收成年牛；三只牛所在结构显示爆满并禁止新放入、繁育和正常内部交互。

若本次拆分得到的所有新结构都无法单体容纳某记录，服务器优先在安全位置生成恢复了年龄、生命和 profile 状态的真实实体；找不到安全位置或实体类型未知时，掉落一个 `sealed_creature` 记录物品。该物品只含一条有界记录和原 record_id。正常扳手拆卸仍优先生成可恢复工具；普通破坏和爆炸按“先分配、再安全实体释放、最后记录物品”的顺序处理。任何转移都遵循 journal：`PREPARE -> DESTINATION_COMMITTED -> SOURCE_TOMBSTONED -> COMPLETE`，重复执行只推进状态，不再产生第二份记录。

## 7. 舱口和玩家收容

- 安山/黄铜舱门口是两格高卷帘门，只能挂在高度至少 2 的结构边界，并把门的朝向写入结构状态。
- 安山/黄铜小舱口占一格，只允许鸡和 profile 声明允许的幼年动物通过。
- 玩家把动物带到舱口附近，用纸棍朝舱口方向击打。纸棍只产生方向意图和短冷却，不调用真实伤害、不施加击退、不把实体推进墙体。服务器验证距离、视线、门状态和容量后，将实体快照写为记录并移除实体。
- UI 选择记录后可弹出为收纳工具；拿工具对目标位置右键，先验证空间和方块可站立性，再一次性生成实体并消费工具。未知实体只能回到封存工具，不能冒险生成。

## 8. 插件和交互处理器

插件是带版本的 `AirCratePlugin` 实例，声明能力、空气/动力需求、输入输出端口和 UI 页签。首批能力包括过滤、繁育、生产、自动吸收、加工和交易；插件缺失、配置不兼容或能源不足时结构进入暂停而不是丢物品。

航空箱不提供普通容器菜单。玩家配置繁殖等模式时，使用外侧半隐藏的 Create 风格值框：悬停后显示，右键拖动滑块修改值；客户端只发送请求，服务器验证控制器、权限和结构版本后提交。该值框是世界内交互，不打开独立 Screen。空手状态提示仅用于开发诊断，发布版应由值框、扳手提示和 Jade 信息替代。

生物交互使用 `CreatureInteractionProcessor` 注册表和工具标签：`aircrate:slaughter_tools`（剑、Farmer's Delight 刀）、`aircrate:shear_tools`（剪刀）、`aircrate:milk_containers`（桶）等。处理器只读取记录并向输出缓存写入物品，不能直接调用实体掉落逻辑；未来模组通过标签/注册表扩展，不修改核心 switch。缓存满时处理器返回 `BLOCKED_OUTPUT`，记录保持原状。

## 9. Create 自动化和固定器

所有物品端口暴露标准 NeoForge `ItemHandler`，外接漏斗、溜槽、传送带、安置器、机械臂、动力臂和包裹接口都通过同一能力访问。航空箱本身不消耗动力；机械手/动力臂右键控制器时根据手持工具调用处理器，交易和采集的动力来自外部 Create 结构。

固定器插件安装在 Create 传送带上后，经过的生物在服务器确认目标端口和唯一记录 ID 后转为记录物品；传送带上的记录 shift+右键释放为实体。生物漏斗作为传送带末端或接收记录物品时，在目标空间可用且区块加载时自动生成实体，否则保持物品形态。固定器传送带可与舱口双向交互。Create 物流只负责运输 `sealed_creature`/订单物品，不读取或拼接任意实体 NBT。

## 10. 生命支持和兼容层

基座定义 `AirSupply`, `FilterState` 两个小型接口；航空箱本身不消耗动力，自动化动作由外部 Create 机械手、动力臂、传送带和物流网络驱动。Create:Survival 兼容只实现适配器：毒雾采样、洁净压缩空气、过滤器消耗、水系统、Curios 背架、Sable 物理体和 Create 6.0 包裹物流。适配器通过 `ModList`/服务加载器延迟发现；任何缺失都退回基座规则。

不要复制 `createsurvival` 的毒雾代码、SavedData 或内部类。兼容 API 需要公开“采样浓度/供气/过滤器消耗/物理实体封存”最小契约，并用版本号和能力探测处理未来 API 变化。Sable 的实体快照格式必须由 Sable 作者公开授权后再实现；在此之前只保存航空箱自己的记录。

## 11. 避难所与村庄生成可行性

建议分时代实现：

1. 新区块生成时，在毒雾阶段和生物群系/结构条件满足时，通过 NeoForge 结构生成或后处理事件生成废弃村庄与避难所；避难所模板内预放航空箱并写入有限空气、过滤器、动力和维护状态。
2. 已生成自然村庄默认不修改、不删除、不替换，避免破坏玩家建筑。管理员可选择一次性、半径受限的迁移命令，将空置村庄标记为“可升级候选”，仍需玩家确认。
3. 结构生成必须使用稳定模板 ID、放置日志和区块级幂等标记，避免重载重复生成。避难所中的村民仍是实体或记录，交易服务受库存和生命支持状态限制。

FTB Unearthed 当前不在本地依赖目录，网上可访问页面也未提供可直接复用的本地源码许可证明。因此本项目只把“多方块收容生物”的玩法作为待核对参考，不复制其代码、资源、数据文件或私有格式。实现前必须记录官方页面、实际收容/分裂机制、源码仓库和许可证；若只有整合包授权或 All Rights Reserved，则仅做独立实现和概念引用。

## 12. 交易网络（仅数据模型，不在第一阶段实现）

地面避难所和飞船村民共享原版村民职业/等级/部分交易，但服务能力由外部上下文决定：

```text
SettlementContext {
  settlement_id, region_id, loaded_inventory_snapshot,
  resident_capacity, air/power/filter/maintenance state
}
ShipContext {
  ship_id, installed_modules, cargo_snapshot, route_state
}
TradeOrder {
  order_id, seller_profile, buyer, offer, request, quantity,
  context_kind, destination, expiry, weather_risk, toxic_fog_risk,
  freight_fee, urgency_fee, insurance, status
}
```

地面避难所负责区域资源、当地库存、批量交易和稳定聚落服务；飞船村民只提供依赖船上模块和库存的应急/小批量服务。远程交易创建订单/报价/合同，实际货物由 Create 包裹物流运输，不能直接打开未加载区块的实体交易界面。天气、毒雾高峰、运费、加急、保险和配送中断只影响订单状态与费用，不绕过库存。

为防止玩家把最好的村民全部搬上船，后续规则应给地面避难所保留最低职业覆盖、区域服务声望和不可迁移的聚落设施；具体数值和经济奖励必须单独讨论后再实现。

## 13. 第一阶段之后的验收顺序

1. GameTest 覆盖 1x1、边界尺寸、连接/拆分、唯一控制器和区块重载。
2. 用 cow profile 验证成年/幼年成长预留、爆满和五牛分裂例。
3. 测试记录转移 journal 在服务器重启、区块卸载、爆炸和重复网络包下不复制/不丢失。
4. 接入 ItemHandler、固定器和生物漏斗，再验证缓存满/动力不足时无损暂停。
5. 以独立 mock API 测试 Create:Survival/Sable 兼容；真实外部 jar 到齐并获许可后才写适配器。
6. 最后讨论避难所生成和交易经济，形成单独版本的规则文档。
