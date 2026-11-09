package cn.academy.port.highpower;

import net.minecraft.world.phys.Vec3;

/** Read-only native fields needed by NEW342's closed connection-cycle observer. */
public interface NativeEmergencyReturnMovementAccess {
    boolean academy$emergencyJumping();
    int academy$emergencyLerpSteps();
    boolean academy$emergencyImmobile();
    Vec3 academy$emergencyStuckSpeed();
    float academy$emergencyBlockSpeedFactor();
}
