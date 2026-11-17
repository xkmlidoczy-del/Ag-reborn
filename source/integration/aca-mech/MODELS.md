# 泰克风格方盒模型

0.4.3 的工程在 `art/v0.4.3/`，ATLAS 446 盒，AETHER 409 盒，SERAPH 587 盒。每台新增 `hip_ram_l` 和 `hip_ram_r` 两组外露髋部油缸。筒身和底座在骨盆，另一端铰点在大腿。修改铰点或外壳位置时保留 `.body`、`.rod`、`.pin` 和 `.mount` 的组名与归属。模型工程保存方盒、枢轴和贴图。鞋底锁定、脚步和油缸的联动在游戏里计算。

0.4.2 的编辑工程在 `art/v0.4.2/`。ATLAS 420 盒，AETHER 383 盒，SERAPH 561 盒。每台增加左右两组 `field_shutter`、`cool_mount` 和 `heat_door`，枢轴分别在护板和散热盖的接缝。盖子打开时露出散热片和自发光管线。继续使用现有 ImageGen 钛、钢和铬材质。方盒工程保留几何、枢轴与贴图；驾驶 IK、后坐、散热轨迹和战斗反馈在游戏代码中运行。

0.4.1 的编辑工程在 `art/v0.4.1/`，ATLAS 384 盒，AETHER 351 盒，SERAPH 529 盒。三台新增独立的油缸、滑杆、两端铰耳和活塞套筒。游戏读取 `*_ram_*.body`、`*.rod`、`*.pin` 的枢轴位置来计算联动，编辑时保留这些组名及归属关节。装甲和轴承采用新的金属材质母图，模型仍使用每台独立的 Box UV 贴图。[母图来源和提示词](../art/v0.4.1/imagegen/PROMPT.md)

0.4.0 的编辑工程在 `art/v0.4/`。ATLAS 354 盒、AETHER 321 盒、SERAPH 487 盒。胸部、腕部与两侧翼端各有独立的发射器骨骼、伸缩套筒和两片挡板。驱动代码通过这些骨骼播放充能和后坐动作。模型沿用原有泰克材质图源，下表记录加入远程模块前的 0.3.0 工程。

| 工程 | 盒体 | 关节 | 光晕发光点 | 贴图尺寸 |
|---|---:|---:|---:|---|
| [ATLAS / 09](../art/v0.3/atlas.bbmodel) | 338 | 18 | 8 | 1024 × 1024 |
| [AETHER / 02](../art/v0.3/aether.bbmodel) | 305 | 18 | 7 | 1024 × 512 |
| [SERAPH / 03](../art/v0.3/seraph.bbmodel) | 455 | 32 | 18 | 1024 × 512 |

ATLAS 的深蓝钢胸甲围住八角反应堆，铜色轴承和小型琥珀警示线与蓝白能源色做区分。AETHER 用拉丝银钛甲与窄导能轨，前臂与小腿都加了切边。SERAPH 的十片翼叶各有独立轴、银色前缘和青绿推进槽。三款把装甲的底座、外板和削角做成有厚度的盒体。锁紧螺帽、护套、活塞和轴盖各自独立。风格参照 Mek 的切角甲片、深色内构和窄能量线。[Nathan Lange 的 ARK Mek 作品页](https://n8studios.artstation.com/projects/PmoVy1)

三份工程使用 Java Modded Entity 和 Box UV。逻辑 UV 每单位对应两像素。ImageGen 分别生成三套钢、钛和铂色材质板，按盒体表面采样后做成游戏的颜色贴图。抛光面和拉丝面随视角出现不同强度的高光。能源件有自发光贴图，部分能源件还有随关节动画移动的渐隐光晕。Minecraft 客户端可直接绘制这些效果。周围方块的光照按原版规则计算。[原始 ImageGen 提示词](../art/v0.3/imagegen/PROMPTS.md) · [模型转换记录](../art/v0.3/conversion.json)

在 Blockbench 打开 `.bbmodel` 可编辑每个盒体。相邻 PNG 是颜色贴图，工程也嵌有副本。修改 PNG 后请保存到模型旁边。导出工具根据网格名生成高光和分层发光数据。

- `metal_polished__` 用于抛光切边、活塞和锁紧件。
- `metal_brushed__` 用于大面积金属装甲，`metal_glass__` 用于舱窗。
- `metal_satin__` 用于消光金属、油缸壳体和机架。
- `emissive__` 保留表面自发光。要加光晕，可参考现有部件的 `emissive__halo_soft__`、`emissive__halo_core__` 或翼片的命名。
- 改动模型时保留单张 Box UV 贴图、整数盒体尺寸与现有关节名。

重新导入游戏资源可运行以下命令。

```sh
.venv/bin/python tools/package_boxes.py
./gradlew build
```

用交付的图源重新生成 0.3.0 模型会覆盖该版本的编辑工程。重建时将模型包中的 `reference/atlas-09/` 合并进源码根目录。

```sh
blender -b -t 4 --python-exit-code 1 --python tools/fit_boxes.py
.venv/bin/python tools/refine_boxes.py --model-version 0.3
.venv/bin/python tools/bake_imagegen_uv.py --model-version 0.3
```

用 `--model-version 0.4.3` 重建当前工程。旧的机型配色图源在 `art/v0.3/imagegen/`，共用车削材质在 `art/v0.4.1/imagegen/`。

原始 GLB 来自 Ramon Linares 的原创机甲库。模型包内含三套 GLB、许可证和校验值。CC0 涵盖这些原始模型的绑定与贴图。[固定上游版本](https://github.com/RamonLinares/atlas-09/tree/1b8fb9d54160215c071c5a29a49b1c36dc01f0df) · [原模资产许可](https://github.com/RamonLinares/atlas-09/blob/1b8fb9d54160215c071c5a29a49b1c36dc01f0df/ASSET-LICENSE.md)
