# ACA Mech 模型来源与编辑

`art/v0.4.3/` 包含 ATLAS、AETHER、SERAPH 的 Blockbench 方盒模型和对应贴图。游戏加载 `../../src/mech/resources/assets/acamech/` 中已导出的模型和材质。

原模型来自 Ramon Linares 的 ATLAS / 09、AETHER / 02 和 SERAPH / 03，固定上游版本：<https://github.com/RamonLinares/atlas-09/tree/1b8fb9d54160215c071c5a29a49b1c36dc01f0df>。模型转换与贡献者持有权利的新增像素贴图采用 CC0，完整声明见 [NOTICE.md](NOTICE.md)、[ASSET-LICENSE.md](ASSET-LICENSE.md) 和 [CC0-1.0.txt](CC0-1.0.txt)。机甲代码及文档另依 [MIT 许可](LICENSE)提供。

编辑 `.bbmodel` 后，在本目录使用 Python 虚拟环境安装 `requirements-models.txt`，运行 `python tools/package_boxes.py`，将变更导出到游戏资源，再从 `source/` 运行 `./gradlew jar`。不带 `--refit` 的工具以本目录 `art/v0.4.3` 中的现有方盒工程为输入，无需原始 GLB；从原始网格重建几何则需要额外的源素材和步骤。
