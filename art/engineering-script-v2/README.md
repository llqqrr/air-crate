# 航空箱资产工程预览 · Create 原材质重制 02

本轮交付 8 张 1920×1280 工程图、17 个可编辑 Blockbench 状态模型、透明 PNG 源材质，以及收纳器的工作动画。请打开 `index.html` 浏览全套，或查看 `00_overview.png`。

制作采用 Python 像素处理、原版模型 / UV 解析、Blockbench 导入和实播验证，没有使用 AI 生图。工程图的六面投影直接来自所附模型，贴图采样使用最近邻。工程图上的文字和缩略总览属于排版，不是游戏贴图。

## 本轮设计

| 资产 | 本版的具体处理 |
| --- | --- |
| 航空箱 | 保留 Create 保险库深灰蓝板材、铆钉与明暗。示例为沿 Z 轴的 1×1×3 结构，两端固定，四个长面循环封闭→玻璃→栅栏。整个示例只有一个端部风机总成。栅栏复用 Create Deco 工业铁栏杆像素，端角添加工业铁加强条。 |
| 交互舱口 | 12×12 的贴片，安装背板厚 2 个模型单位，操作口额外凸出约 1 个单位；不是完整方块。保险库面板、缩小的原打包机操作口和独立护理标牌组合。 |
| 回响激发器 | 原安山机壳底座、幽匿环带、原版校频感测体紫晶交叉片。提供静止 / 激活两态；激活仅调亮青色脉络，没有烘焙模糊光晕。 |
| 生物打包机 | 基础模型直接使用 Create 6.0.10 `block/packager/item.json`，保留全部 8 个元素和 UV。以 Fluid Logistics 的铜色替换区域为依据，改用 Create 黄铜机壳色阶。提供基础、充能、联动模型；朝下模型仅为整体朝向示意。 |
| 生物包裹 | 沿用前版大窗方案，壳体和五个面的 UV 回到 Create 原 12×12×12 纸箱。前窗可透明；示意羊来自前版包裹的独立模型分组，没有画进贴图。 |
| 混合饲料 | 原版小麦像素组成谷物束，新画麻绳、苹果碎块、种子。16×16 RGBA，无碗。 |
| 生物过滤器 | 使用实际 `create:item/filter` 卷纸外形，两侧轴杆、纸张边缘和透明轮廓不变，仅在内容区添加短条目与爪印。 |
| 生物收纳器 | 原 Extendo Grip 握持部和连杆，配均匀缩小的黄铜打包头及金属夹持翼。三维模型提供收拢 / 伸出两态，以及 1.6 秒伸出、开口、夹持、闭合、回缩动画。 |

生物蛙港未修改。本轮文件全部放在独立美术目录，未替换模组运行资源，也未修改游戏逻辑。

## 原材质来源与改动范围

- **Create 6.0.10 / Minecraft 1.21.1**：来自本项目 `libs/create-1.21.1-6.0.10.jar`。使用保险库、打包机、鼓风机、伸缩机械手、机壳、传动杆、列表过滤器和原纸箱资源。已将所用原始文件保存在 `references/create/`。官方对照标签为 [mc1.21.1-6.0.10](https://github.com/Creators-of-Create/Create/tree/mc1.21.1-6.0.10)，核对过 `packager_frame.png` 与本地来源的 SHA-256 一致。
- **Create: Fluid Logistics**：沿用项目此前保存的原始 `packager_*.png` 对照素材，存放在 `references/fluidlogistics/`；未验证其具体发布版本。脚本比较两套同名像素，选出差异中的铜色区域，再映射到原黄铜机壳色阶。它是此处的换色位置参考，不是重新发明一套外壳。
- **Create Deco**：`talrey/CreateDeco` 的 `1.21-neo` 分支，固定提交 `996780bed4549d7b1d0dec65f06d5129e69330ba`。使用 `industrial_iron_bars`、对应辅助材质和 `industrial_iron_plate_metal`。源文件及上游 CC0 LICENSE 位于 `references/createdeco/`。[上游资源](https://github.com/talrey/CreateDeco/tree/996780bed4549d7b1d0dec65f06d5129e69330ba/src/main/resources/assets/createdeco/textures/block/palettes)。
- **Minecraft 原版资源**：来自本机 `Desktop/assets（mc本体）/minecraft`，拷贝在 `references/minecraft/`。包括幽匿、校频紫晶、小麦和苹果。没有据此推断未核实的客户端版本。
- **前版包裹中的示意生物**：只复用 `engineering-script-v1` 的独立示意羊几何和颜色。对应源工程完整备份为 `references/previous_parcel_display.bbmodel`，重建无需依赖旧目录。

新画或改写的内容包括：玻璃开口与反光点、原栏杆与保险库边缘的组合、护理标牌、前窗边缘、饲料配料、过滤器内容和幽匿激活明度。`SOURCE_MANIFEST.json` 逐张记录操作、来源、尺寸和输出 SHA-256；`mask_audit.json` 记录每张打包机图的换色像素数量及掩膜外一致性。

原打包机 item 模型本身是开放框架。此处预览保留其空载结构；游戏中由渲染器另外放入的包裹没有伪造为白色机身面板。

## 文件与验证

- `sheets/`：8 张完整工程图，含三维预览、六面投影（平面物品除外）、实际源贴图及色板。
- `models/`：17 个 free 格式 `.bbmodel`，内嵌纹理，可直接用 Blockbench 打开。用于结构和美术审阅，不是即装即用的 Minecraft Java JSON。
- `native_models/`：3 个打包机原生 JSON 研究稿，仅改材质引用，元素数据保持原样。`aircrate_concept` 是预览命名空间，尚未接入模组资源路径。
- `textures/`：透明 PNG。新物品稿为 16×16；Create 原 32×32、64×64 图集和原版动画条保留原始尺寸，避免强行缩图破坏 UV 与像素密度。多零件机械模型不伪装成六张独立 16×16 整面纹理。
- `08_catcher_work_cycle.gif`：固定相机、20 fps、32 帧，来自真实 Blockbench 渲染器逐时刻截图。
- `08_catcher_structure_motion.gif`：脚本生成的固定相机结构预演，单独保留，便于无 Blockbench 环境重建。
- `blockbench_exports/08_catcher_animated.bbmodel`：由实际 Blockbench project codec 重新导出的动画工程。选择 `capture_cycle` 播放；闭口与开口使用分组缩放切换，连杆运动以 0.05 秒间隔采样。
- `renders/blockbench_*.png`：实际 Blockbench 打开及动画关键时刻截图。
- `validation.json`、`BLOCKBENCH_VALIDATION.json`：脚本检查和真实编辑器加载结果。17 个模型的元素、纹理、动画数均与导入前一致；验证了过滤器边缘不变、换色掩膜外不变、航空箱单一通风口及动画帧实际变化。

## 后续接入范围

这轮是工程预览与可编辑模型稿，没有启动 Minecraft 做实机测试。5×3×32 全尺寸连接纹理、四个长面的独立扳手切换、朝向与渲染层、物品第一 / 第三人称握持、光照和生物实时渲染，需要下一轮按模组代码接入。打包机朝下示意不是完整的 Create 垂直连接状态；所需原始垂直模型和图集已备份供接入时复用。

## 重建

Windows 环境使用 Python、Pillow、NumPy，工程图字体为系统微软雅黑。

```powershell
python build_v2.py
python verify_blockbench.py
python make_review_board.py
```

第二步需要正在运行的 Blockbench 及已安装的 MCP 插件（`localhost:3000/bb-mcp`）。它加载本目录工程、捕获预览并返回编译内容，由 Python 保存输出；不修改游戏或其他工程文件。第三步生成便于浏览的总览与换色依据对照图。
