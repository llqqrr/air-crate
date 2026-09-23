# 航空箱模型接入资源 V4 · 给 Kimi 的交接

依据：已读取 `LQR模组` 下 Kimi 会话 **机械动力航空箱** 最后一轮用户反馈、排查报告及相关截图；会话 ID `session_293c54e6-a681-4fbc-824e-04be090aabe6`。对照当前工程的 `bbmodel_convert.py`、`gen_crate_faces.py`、`CrateRenderData`、`CrateShellModel` 和原始 V2 模型制作。

这是新增的模型接入包。没有改写 `src/main`，没有替换测试整合包里的 jar，没有实机验证。资源级检查通过不代表现有 Java 渲染代码已经修好。

## 本次交付

- `00_review.png`：实际交付 JSON 的脚本投影，含三态航空箱、端面、原生风扇、修整后的激发器。
- `01_tiling.png`：5×3、1×3、3×1 的材质拼接检查。橙色细线是预览辅助线，不在 PNG 材质中。
- `resources/assets/aircrate/`：直接可放进工程 resources 的标准模型和 PNG；使用新路径 `block/integration_v4`，需要接入代码引用。
- `blockbench/`：9 个带内嵌纹理的可编辑工程。6 个独立零件/机器为 `java_block`；3 个整箱示例为 `free`，仅审阅，不能整箱导出成单方块。
- `ASSET_MANIFEST.json`：资源清单、SHA-256、边界位定义。
- `checks/validation.json`：验证范围及结果。

所有新材质为 16×16 RGBA，alpha 仅有 0/255。Create/Minecraft 原图集仍按原尺寸引用，不能缩到 16×16。源素材见 `references/`：Create 6.0.10，Minecraft 校频幽匿感测体，Create Deco 工业铁栏杆（提交 `996780bed4549d7b1d0dec65f06d5129e69330ba`，CC0 文件随包附带）。

## 1. 连接纹理：按整个结构的面选择

四类壁板：`closed`、`glass`、`bars`、`panel`。前三类用于四个长面；`panel` 用于固定的两端面。

模型 ID 模板：

```text
aircrate:block/integration_v4/crate/{kind}_{mask:02}_{direction}
```

`mask` 是**外缘存在**的位掩码，不是邻接存在：左=1、右=2、上=4、下=8。十进制补两位，例如 `glass_05_north`。中心为 00，完整单格为 15。它比普通九宫格多出 1 格宽/高时的对边组合，不能只导入 9 个状态。

```java
int mask = (u == 0 ? 1 : 0) | (u == faceWidth - 1 ? 2 : 0)
         | (v == 0 ? 4 : 0) | (v == faceHeight - 1 ? 8 : 0);
```

每个零件已带外面与内面，内面内移 0.04 **模型单位**（不是 0.04 方块）。不再添加旧的 `_in` 零件。无需启用双面透明排序，也不要添加 `cullface` 让游戏误裁掉远侧内壁。

这里 u 从观察者左向右，v 从上向下；严格采用 Minecraft 1.21.1 `FaceInfo` 的 UV 基底。以结构最小坐标为原点，成员局部坐标为 `(x,y,z)`，整体世界轴尺寸为 `(W,H,L)`：

| 面 | u | v | 面宽 | 面高 |
|---|---|---|---|---|
| north | W−1−x | H−1−y | W | H |
| south | x | H−1−y | W | H |
| west | z | H−1−y | L | H |
| east | L−1−z | H−1−y | L | H |
| up | x | z | W | L |
| down | x | L−1−z | W | L |

模型已按六个方向分别生成，**不要再对这些方向版模型追加旋转或 uvlock**。顶部/底部也根据自己的二维坐标计算外缘，不能复用单个侧面的 mask。相邻且属于同一结构的内部面不提交任何模型。

结构轴、尺寸、控制器必须来自同一份结构数据；避免每个成员独立通过相邻数量猜主轴，尤其要处理长宽相等和结构变更。结构变化与扳手切换后，更新整个结构成员的 ModelData 并请求区块重建。

### 纹理内容

- 玻璃窗口全透明，不加浅蓝灰填色；`glass_inner.png` 完全透明。
- 栅栏保留 Create Deco 的铁柱像素与横向拉条。柱子跨上下拼缝连续；细拉条属于格栅图案，不是误保留的整块边框。
- 封闭模式保留保险库的压纹板材；粗边框只在整体外缘。
- 端面取自保险库 `vault_front_large` 的有效 48×48 区域，3×3 拼图逐像素与其一致。更宽的端面重复中段，1 格宽/高使用紧凑的双边版本；不将整张图拉伸糊到大面上。
- `frame_*.png` 与三张 `*_inner.png` 也分别提供，后续可改为动态拼几何；当前组合图减少接入逻辑。

## 2. 单个立体风扇

只有**选定通风端**的 `u=0 && v=0` 那一格用 `vent`，同端其余格仍用 `panel`。另一端全部用 `panel`。例如主轴 Z、通风端 north 时是 `x=W−1, y=H−1, z=0`；主轴 X、通风端 west 时是 `x=0,y=H−1,z=0`。

```text
aircrate:block/integration_v4/crate/vent_{05|07|13|15}_{north|south|west|east}
```

不要把整个端面的所有成员标成 VENT。这条是运行时选面规则，光换材质不会解决九个风扇的问题。

此模型已包含打孔端板和完整风扇，不要再叠普通 panel，也不要额外叠 `fan_north`。保留原 Create 鼓风机的 Bottom / Top / Side / Side / Lattice / Back / Shaft / Fan 八个元素，统一 0.625 缩放、偏移 `(3,3,-0.5)`；与 V2 原设计相同。前框 z=−0.5，格栅 z=0.125，背板前面 z=5.125，内腔没有被一张放大叶片贴图替代。

需要旋转动画时，改用 `vent_static_XX_direction` 加 `fan_rotor_direction`；静态版包含端板+框+格栅+背板，rotor 只含轴和叶片。**不要同时渲染完整 vent 和 rotor**。

| 方向 | 旋转轴 | 轴心（模型单位） |
|---|---|---|
| north | Z | (8,8,4.5) |
| south | Z | (8,8,11.5) |
| west | X | (4.5,8,8) |
| east | X | (11.5,8,8) |

风扇叶片原有 22.5° 元素旋转已经包含在 JSON 中，动态转动再叠加绕轴旋转。实际速度、停转条件由玩法决定，本包不虚构这些行为。

## 3. 回响激发器

标准模型：`resonator_idle.json`、`resonator_active.json`、`resonator_rotor.json`、`resonator_item.json`。

- 底座由原来 6 高改为 12 高，补齐机壳的上/下边框，木板像素仍保持 1:1；原来只露一侧的偏低凸块改成 north/south 双轴孔。
- 传动轴中心为 `(8,8)`，与相邻普通 Create 传动轴的中心对齐。环带 y=12..16，晶体 y=16..28；需相应考虑方块轮廓/渲染包围盒和顶部空间，不能照旧按矮模型裁剪。
- `idle/active` **不含转轴**，固定机身和转轴分开。`item` 含静态转轴，仅供物品或完整外观预览。
- `resonator_rotor` 沿 Z 轴贯通，绕 `(8,8,8)` 旋转；面向 X 轴时将机身与转轴整体旋转 Y=90°。要真正接动力，方块仍需 Create kinetic 接口/BlockEntity 和速度驱动，模型本身不会提供这些功能。
- 两张紫晶面直接来自原版模型，保留 Y=45°、`rescale:true`、镜像 UV、双向面。未用任意斜面近似成正交方盒。
- 所有 JSON 显式指定 `render_type: minecraft:cutout`。激活版只增亮幽匿脉络，不等同于无光照自发光；若需要真正发光需额外渲染实现。

## 4. 包裹补充模型与排查更正

附 `parcel.json` / `parcel.bbmodel`。纸箱五个不透明面的 face 定义与 Create 原 `cardboard_12x12.json` 完全一致，包括 bottom 的 **270° UV 旋转**、top 的 180° 旋转、背面反向 UV。前面沿用认可的大窗；新增正常法线的内侧面，避免从窗外看到箱壳背面被裁掉。内侧面用正常 from≤to 的薄面，未用倒置包围盒模拟内壳。

已核验：V2 bbmodel 内嵌 cardboard 与本机 `libs/create-1.21.1-6.0.10.jar` 的 cardboard **64×64 RGBA 像素完全相同**。PNG 文件字节不同只是编码不同，不能据此说图集布局不同。当前运行 JSON 确实漏掉了镜像与旋转；换命名空间复制同一张图不会修复 UV。

本补充包修正上述模型缺陷，但“看到了眼镜面”是否还涉及实际显示朝向或渲染器变换，需要游戏内核验，不能仅凭模型文件宣称全部解决。

## 5. 不要再通过旧 free-mesh 转换器处理这些零件

当前 `bbmodel_convert.py` 取所有顶点包围盒、按法线主轴猜朝向、对 UV 只取 min/max，会丢掉：

1. 非轴对齐的几何（风扇、水晶等）；
2. UV 旋转与镜像（包裹底面、鼓风机侧框等）；
3. 多个共向面；
4. 元素/组的变换。

所以“不要有旋转”并不足以保证可转换：即便元素 rotation=0，顶点烘焙后的斜面依然不是轴对齐方盒。本包资源应**直接使用所附 Java JSON**；配套零件 bbmodel 为 Java Block/Item cubes，便于以后在 Blockbench 编辑再导出。

整箱 3×3×5 bbmodel 是 free 审阅模型，尺寸超过单方块 Java JSON 的限制，禁止重新交给那个转换器。

## 6. 运行时仍需完成的事项

1. 将 `resources/assets/aircrate` 合并到工程；按本说明为 CrateRenderData 增加面内坐标和整体尺寸，用新的零件替代旧面片。
2. 为动态外壳声明/解析子模型依赖，保证烘焙前能发现它们的材质；具体使用 `resolveParents` / 正确的模型注册流程，不能仅在 bake 时临时猜路径。
3. 全部外壳使用 cutout；本包没有半透明玻璃，不应再把整箱放进 translucent。保留内部面、按同一结构隐藏共有面。
4. 不要再执行会填充玻璃窗口的旧 `gen_crate_faces.py`；当前脚本仍含 GLASS_FILL，重跑会覆盖恢复的透明原图。
5. 打包机和收纳枪的紫黑问题属于独立模型加载/注册/渲染接入排查，未在此包中重画。对 BEWLR 的 standalone 模型检查 RegisterAdditional；对 Flywheel PartialModel 还要检查类初始化时机与 Flywheel 自身注册流程。不能单凭“本模组没有 RegisterAdditional”就断言所有 partial 都必然未注册。
6. 生物蛙港、枪的造型和已认可的动画资源均沿用上一版。本包没有更改它们，也没有将其它玩法代码回退。

已用本地 JDK 的 `javap` 核对 Create jar 内嵌 Flywheel 1.0.6：`PartialModelEventHandler.onRegisterAdditional` 会遍历 `PartialModel.ALL` 并逐个注册 standalone 模型；`onBakingCompleted` 会给已记录的 partial 赋 bakedModel。因此应检查相关静态字段是否在注册事件前初始化，不能忽略这条自动注册路径。

## 验证与重建

在原项目内执行（Python + Pillow + NumPy）：

```powershell
python air-crate/art/model-integration-v4/build.py
python air-crate/art/model-integration-v4/validate.py
```

检查 434 个 Java JSON、103 张新增 16×16 材质、9 个带内嵌纹理的 Blockbench 工程；参考选面器遍历 `5×3×32×2=960` 种尺寸/水平轴组合，确认每个结构恰有一个左上角通风口、无内部面。另查 16 种边界组合、图集引用、旋转往返、风扇原 UV、端面像素一致性、包裹原 UV。

这些是资源和参考算法验证。现有 Java 代码尚未使用该算法，仍需实机测试 north/west 通风端、1 格高/宽、最大结构、结构拆建重载、三态扳手切换、不同光照、远侧内壁，以及两种渲染器路径。
