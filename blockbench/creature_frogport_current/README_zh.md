# 当前生物蛙港模型导出

这是 Air Crate 当前版本实际使用的生物蛙港模型资源，供建模修改使用。

## 模型部件

- `models/base.json`：底座，固定渲染。
- `models/body.json`：蛙港主体，固定渲染。
- `models/head.json`：蛙港头部，运行时围绕旋转中心做张嘴/俯仰动画。
- `models/tongue.json`：舌头，运行时会沿自身长度方向伸缩并指向选区中心。

四个 JSON 都是 Minecraft Java Block Model 格式，可以在 Blockbench 中通过 `File -> Import -> Minecraft Java Block/Item Model` 导入。它们是独立部件，因为游戏中的 Create/Flywheel 渲染器也按这四个部件分别渲染；不要把头部和舌头的原点改成普通方块原点，否则动画旋转会偏移。

## 贴图

- `textures/creature_frogport_port.png`：当前生物蛙港使用的 Air Crate 换色贴图。
- `assets/create/textures/block/port.png`、`port2.png`：Create 原始蛙港贴图，模型中的 `#1` 和 `#0` 分别引用它们。
- `assets/create/textures/block/froggles.png`：Create 原始护目镜贴图，当前生物蛙港默认不渲染护目镜，但保留作参考。

导入后，如果 Blockbench 无法解析资源引用，可以把 `creature_frogport_port.png` 临时复制为模型材质 `create:block/port.png` 的替代贴图；游戏实际运行时仍使用 Air Crate 的换色贴图。

## 当前运行时动画参考

动画代码位于：

`src/main/java/com/llqqrr/aircrate/client/CreatureFrogportRenderer.java`

关键连接点（Minecraft 方块坐标，1 方块 = 16 模型单位）：

- 头部旋转中心：`(0.5, 0.625, 0.6875)`。
- 舌头旋转中心：`(0.5, 0.625, 0.6875)`。
- 舌头沿局部 Z 方向伸缩。
- 主体和头部先绕方块中心按蛙港朝向旋转，再应用头部动画。

如果只修改外观，建议保持四个部件的名称、局部坐标、旋转中心和贴图槽位 `#0/#1` 不变。需要新增部件时，可以在 `body.json` 或 `head.json` 中添加元素；如果新增部件需要独立动画，则应另建 JSON partial，并同步修改渲染器。

## 原版 Create 参考

`assets/create/models/block/package_frogport/` 中保留了 Create 6.0.10 原版蛙港的完整 Blockbench 模型文件，方便比较原始结构和材质映射。

