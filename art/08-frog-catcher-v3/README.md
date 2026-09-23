# 08 生物收纳器 · 蛙港气动捕获枪 V3

本轮只重做第 08 项。没有修改 01–07、生物蛙港方块本身或模组运行代码；旧版伸缩机械手方案完整保留在上一轮目录。

双击 `viewer/index.html` 可离线打开三维交互预览。Three.js、模型、贴图均包含在页面中，无需联网下载。可以旋转、试射、调节气量、查看第一人称握持示意，并切换原土豆加农炮进行同视角比较。

## 交付

- `sheets/08_frog_catcher_engineering.png`：2000×1460，三维外观、六面结构图、原像素素材及设计约束。
- `sheets/08_mechanism_and_pressure.png`：2000×1480，开口伸舌、压力表三态、原加农炮比例对照、背罐去背板对照。
- `models/08_frog_catcher_animated.bbmodel`：可编辑动画模型，捕获与压力两条独立动画。
- `models/08_frog_catcher_idle.bbmodel`、`08_frog_catcher_firing.bbmodel`、`08_frog_catcher_low_pressure.bbmodel`：静止、发射、低压姿态。
- `08_capture_cycle.gif`：按保存的模型动画轨道进行脚本采样渲染的动作预览，30 帧；不是 Blockbench 屏幕录制。蒸汽粒子在交互页面中演示。
- `00_updated_design_board.png`：沿用上轮 01–07 图像，仅替换 08 的新总览。
- `SOURCE_MANIFEST.json`、`validation.json`、`geometry_validation.json`：素材来源、尺寸及模型检查。

## 结构与像素来源

**蛙港头部**使用本项目 `air-crate/src/main/resources/assets/aircrate/models/block/creature_frogport_{head,body,tongue}.json` 及当前 `creature_frogport_port.png`，解析其 Create 父模型。保留双眼、上下颌、嘴部金属边缘和原舌头贴图，去掉底座和蛙身的无关支脚。整体均匀缩放到手持工具尺寸，没有用整块蛙港作枪口。

**铜气罐**来自本项目 `libs/create-1.21.1-6.0.10.jar` 的 `block/copper_backtank/block.json`。仅使用铜壳元素 0、1、2，去除背负衬板及附属结构 3、4；派生图集清除了下方背负棕色部件区域，实际铜壳像素保持原样。铜壳横置、均匀缩放，以新做的金属箍带和黄铜鞍座装在机匣上。尾端增加黄铜端盖和泄压阀。原始背罐完整模型只用于来源对照。

**枪身与握把**复用原黄铜机壳、安山机壳与机械冲压杆材质，细条采用裁剪 UV，避免把整张机壳缩到一条梁上。安山握把向后下方倾斜 25°，黄铜扳机独立建模。齿轮直接复用 Create 土豆加农炮的 `item/potato_cannon/cog.json` 和原图集，设置在扳机前方的开放机构槽。

**压力表**参考 Create Diesel Generators 发酵罐的框式表盘。参考源码固定在提交 `ce767d9504d1a039f79909e1a86dd33731d560eb`，来自 [george8188625/Create-Diesel-Generators](https://github.com/george8188625/Create-Diesel-Generators/tree/ce767d9504d1a039f79909e1a86dd33731d560eb)。源参考图及上游 LICENSE 在 `references/diesel/`。本版重新画 6×6 像素内容区、8×8 边框，存放于 16×16 透明画布；指针和中心轴帽为独立几何，并非固定画在图里。

**排气口**在枪身侧后，黑色内口与黄铜接头独立建模；交互预览发射后喷出 5 个逐渐消散的小立方粒子。粒子不是烘焙纹理。

## 接缝、闭嘴与运动空间

气罐已前移，蛙港后部与铜罐间的外观空隙由下方气路连接颈衔接。上颌抬起时同步短程前移，避让紧凑安装的罐体；下方连接颈没有伸入上颌活动空间。

Create 原蛙港包含供单面渲染使用的反向内表面。这些面在双面网格预览中会和外表面争夺深度，本版已去重。另裁掉上下颌闭合时相互覆盖的上颌裙边，UV 按裁切插值保留原密度；口内两张闭合面留有微小高度差。几何检查覆盖重复面、闭合间隙及动画期间上颌对罐体的前后间距。

## 压力表的实际联动

预览采用 8 次容量，每次有效试射减少 1 次。页面上剩余次数、百分比、指针都读取同一个气量状态；余量 ≤25% 显示红区预警，归零禁止试射，补气后恢复。

```text
fraction = clamp(air / maxAir, 0, 1)
needleAngleX = 70 - 140 × fraction   // full=-70°, empty=+70°
remainingCaptures = floor(air / airCostPerCapture)
```

8 次仅是设计稿演示参数，不是已经决定的游戏平衡数值。真正接入时应由服务端在捕获成功时扣气，并将同步后的气量驱动指针；不要在客户端单凭试射动画扣除真实资源。

## 动画接口

| 分组 | 驱动方式 |
| --- | --- |
| 蛙港上颌_含双眼 | 0→72° 绕 X 旋转，同时沿 Z 前移 `-1.8×sin(angle)` |
| 蛙舌_伸缩 | Z 缩放 0.1→4.1→0.1，原舌头轴向伸缩 |
| 驱动齿轮 | 发射及回收阶段绕 X 旋转 |
| 黄铜扳机 | 扣动时后压 16°，随后复位 |
| 压力指针 | 由气量控制 X 角度，与捕获周期独立 |
| 尾部泄压阀 | 排气阶段短行程伸出，再复位 |

`capture_cycle` 长 1.2 秒；`pressure_full_to_empty` 为 1 秒参数扫描，时间 0 对应满气、时间 1 对应空罐。后续渲染器应把压力扫描的时间映射到 `1-fraction`。蒸汽约在 0.46 秒触发，约 0.3 秒消散。

## 比例与接入范围

静止全长约 18.32 模型单位，原土豆加农炮为 20。源 JSON 的第一 / 第三人称 display 参数保留在工程中。网页握持预览采用原加农炮显示参数与标准持物锚点，目的是同视角比较；它没有玩家手臂，不能替代 Minecraft 中的 FOV、光照、动画和遮挡测试。

本轮是美术工程稿和功能交互原型。`.bbmodel` 使用可动画的通用网格格式，不能直接当作 Minecraft Java 方块 JSON。实体捕获判定、资源消耗、充气方式、粒子事件、物品渲染器及动态指针还需下一轮接入模组。PNG 都是逐像素清晰的 RGBA；Create 原 32/64 图集按原尺寸保留。

## 重建

依赖 Python、Pillow、NumPy；排版使用 Windows 微软雅黑。素材已备份在本目录。

```powershell
python build_frog_gun.py
python build_viewer.py
python make_sheets.py
python validate_geometry.py
```

`viewer/three.min.js` 是 Three.js 0.160.1，许可在 `viewer/THREE-LICENSE.txt`。页面可通过本地 HTTP 服务或直接打开 HTML 使用。`bb_bridge.py` 保留为现有 Blockbench MCP 插件可用时的辅助工具；它不是打开 `.bbmodel` 的前提。
