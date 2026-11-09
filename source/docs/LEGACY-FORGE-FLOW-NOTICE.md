# Legacy Forge phase-fluid compatibility provenance

AcademyCraft1.0.7 extends MinecraftForge1.7.10 `BlockFluidClassic`. The exact
three-quanta update, density/displacement and render-height rules in this
increment were checked against the official original implementation:

- https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/fluids/BlockFluidClassic.java
- https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/fluids/BlockFluidBase.java
- https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/fluids/RenderBlockFluid.java

The checked branch's licence is **Minecraft Forge Public Licence, version1.0**:
https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/MinecraftForge-License.txt

Do not substitute NeoForge's current LGPL notice for this historical source.
Forge-derived portions retain their original licence; new native adapters and
AcademyCraft portions retain the project's existing terms. The official links
provide the checked licence/source. No MCP data, original Minecraft code or old
Forge binary is bundled by this increment. This task is local development only;
external redistribution and resolving licence compatibility remain outside its
authorization and are not claimed by the port.
