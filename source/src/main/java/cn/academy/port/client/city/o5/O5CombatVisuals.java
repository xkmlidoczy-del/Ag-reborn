package cn.academy.port.client.city.o5;

import cn.academy.port.city.defense.O5OperativeEntity;
import net.minecraft.client.Minecraft;

final class O5CombatVisuals {
    static float contactAge(int owner,double now){var level=Minecraft.getInstance().level;return level!=null&&level.getEntity(owner) instanceof O5OperativeEntity e?(float)(now-e.contactTick()):100;}
}
