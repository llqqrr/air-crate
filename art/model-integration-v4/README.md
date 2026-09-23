# 模型接入修订 V4

先看 `00_review.png` 和 `01_tiling.png`。

完整说明见 [INTEGRATION.md](INTEGRATION.md)。将该说明和整个目录交给 Kimi 即可：`resources/` 是标准 Java 模型资源，`blockbench/` 是带内嵌材质的编辑/审阅工程，`checks/validation.json` 是校验结果。

本轮没有替换游戏内资源，没有部署 jar。不要直接把整箱示例 bbmodel 转成单方块；实际应按整体结构选取 `resources` 中的分面零件。
