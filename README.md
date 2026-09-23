# Air Crate / 航空箱

独立的 NeoForge 1.21.1 模组工程，为机械动力生态提供可组合的玻璃航空箱。工程不依赖《机械动力：生存》（`createsurvival`），也不复制 Create:Survival、FTB Unearthed 或其他模组的代码和资源。

## 固定身份

- 工程目录：仓库根目录（`air-crate`）
- mod id：`aircrate`
- Java 包：`com.llqqrr.aircrate`
- Maven group：`com.llqqrr.aircrate`
- 初始版本：`0.1.0`
- 主方块注册名：`aircrate:aviation_crate`

## 依赖策略

基座运行时要求 Minecraft `1.21.1`、NeoForge `21.1.219+` 和 Create `6.0.10+`，使用 NeoForge `ItemHandler` 和 Data Components 保存结构化记录。Ponder、Curios、Carry On、Jade、Sable、Aeronautics 等属于可选集成；Create:Survival（`createsurvival`）不是依赖，也没有声明兼容入口。

Create 相关代码位于单独的 `compat/create` 源码边界；可选模组不能让基座在类加载时直接引用其类。这样没有任何可选兼容模组时仍能启动和使用基础航空箱。

## 构建

中文路径可能触发 NeoForge 工具链问题。推荐将仓库复制或映射到仅包含 ASCII 字符的目录后，从仓库目录调用父工程 wrapper：

```powershell
Set-Location <ascii-path>\air-crate
..\gradlew.bat --no-daemon --gradle-user-home .gradle-home build
```

输出为 `air-crate/build/libs/aircrate-0.1.0.jar`。第一阶段不安装或启动 Minecraft；完成实现后再由用户把 jar 放入测试整合包并重启测试。

## 设计入口

完整设计、数据契约、容量/重组算法、兼容边界和交易网络见 [`docs/AIR_CRATE_DESIGN.md`](docs/AIR_CRATE_DESIGN.md)。阶段性交接和未决问题见 [`docs/AIR_CRATE_HANDOFF.md`](docs/AIR_CRATE_HANDOFF.md)。
