package cn.academy.port.client.highpower.mixin;

import cn.academy.port.client.WindPressureLiftNativeTravel;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** LocalPlayer-only dispatch wrapper. Shared LivingEntity/Player native methods remain unchanged. */
@Mixin(LocalPlayer.class)
abstract class WindPressureLiftTravelMixin extends AbstractClientPlayer {
    protected WindPressureLiftTravelMixin(ClientLevel level, GameProfile profile) { super(level, profile); }
    @Override public void travel(Vec3 vector) {
        var ticket = WindPressureLiftNativeTravel.beforeTravel((LocalPlayer)(Object)this, vector);
        try { super.travel(vector); }
        finally { WindPressureLiftNativeTravel.afterTravel(ticket); }
    }
}
