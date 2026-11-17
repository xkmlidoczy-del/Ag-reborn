# 0.4.5 驾驶舱和能量贴图

三张母图使用内置 ImageGen 分别生成。通过 `.venv/bin/python tools/bake_cockpit_fx.py` 导入游戏。驾驶舱图保留金属与玻璃颜色，仪表读数、准星和按键提示由游戏绘制。两张能量图转为亮度遮罩，在充能、发射和命中时按机体颜色叠加。

`cockpit.png`

> Use case: ui-mockup. Wide mech cockpit HUD panel, straight-on. Thin machined graphite titanium rim, restrained ice-blue edge lights, dark navy smoked glass. Quiet empty center, every corner visible. No text, symbols or reflections.

`charge-ring.png`

> Use case: stylized-concept. Square game VFX texture of one centered hollow ring of broken white plasma filaments and tiny sparks. Thin sharp edges, soft falloff, pure black background. No text.

`impact-burst.png`

> Use case: stylized-concept. Square game VFX texture of a compact white-hot energy impact with branching sparks and smoky filament falloff. One centered burst on pure black, no scenery or text.
