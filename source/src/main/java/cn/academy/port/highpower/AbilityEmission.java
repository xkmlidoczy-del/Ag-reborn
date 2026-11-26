package cn.academy.port.highpower;

import java.math.BigDecimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Closed server-controller capabilities; neither packets nor callers can manufacture an emission. */
public sealed interface AbilityEmission permits NewSkillRuntime.EmissionPermit, ConstructRuntime.ImpactPermit, ConstructRuntime.SplashPermit, PointMeltdown.EntityPermit, BeamEtching.EntityPermit, CanonEmission.Permit, CanonEmission.ConstructPermit {
    ServerPlayer player();
    LivingEntity target();
    String skill();
    BigDecimal damage();
    boolean claim();
    boolean valid();
}
