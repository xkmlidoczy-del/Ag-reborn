package cn.academy.port.fleet.mixin;
import cn.academy.port.fleet.client.QaAvCapture;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SoundEngine.class)
public abstract class SoundTraceMixin {
 @Inject(method="play",at=@At("HEAD"))private void aclab$trace(SoundInstance s,CallbackInfo c){QaAvCapture.sound(s);}
}
