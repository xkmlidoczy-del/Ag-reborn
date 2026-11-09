package cn.academy.port.highpower;

import java.util.UUID;
import net.minecraft.world.entity.Entity;

/** Read-only fields: Projectile.getOwner() is deliberately not an observer getter (it fills its cache). */
public interface NativeScatterArrowOwnerAccess {
    Entity academy$scatterCachedOwner();
    UUID academy$scatterOwnerUuid();
    boolean academy$scatterLeftOwner();
}
