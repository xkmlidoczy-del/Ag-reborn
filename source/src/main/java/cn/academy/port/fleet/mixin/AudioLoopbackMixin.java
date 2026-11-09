package cn.academy.port.fleet.mixin;
import com.mojang.blaze3d.audio.Library;
import cn.academy.port.fleet.client.QaAvCapture;
import java.nio.*;
import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Library.class)
public abstract class AudioLoopbackMixin {
 @Shadow private long currentDevice;
 @Inject(method="openDeviceOrFallback",at=@At("HEAD"),cancellable=true)
 private static void aclab$device(String name,CallbackInfoReturnable<Long> c){if(QaAvCapture.ENABLED){long d=SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer)null);if(d==0)throw new IllegalStateException("OpenAL loopback unavailable");c.setReturnValue(d);}}
 @Redirect(method="init",at=@At(value="INVOKE",target="Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J",remap=false))
 private long aclab$context(long d,IntBuffer original){if(!QaAvCapture.ENABLED)return ALC10.alcCreateContext(d,original);try(var stack=MemoryStack.stackPush()){return ALC10.alcCreateContext(d,stack.ints(ALC10.ALC_FREQUENCY,QaAvCapture.RATE,SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT,SOFTLoopback.ALC_STEREO_SOFT,SOFTLoopback.ALC_FORMAT_TYPE_SOFT,SOFTLoopback.ALC_FLOAT_SOFT,0));}}
 @Inject(method="setHrtf",at=@At("HEAD"),cancellable=true)private void aclab$hrtf(boolean on,CallbackInfo c){if(QaAvCapture.ENABLED)c.cancel();}
 @Inject(method="init",at=@At("TAIL"))private void aclab$ready(String name,boolean hrtf,CallbackInfo c){if(QaAvCapture.ENABLED)QaAvCapture.attach(currentDevice);}
 @Inject(method="cleanup",at=@At("HEAD"))private void aclab$close(CallbackInfo c){if(QaAvCapture.ENABLED)QaAvCapture.detach();}
 @Inject(method="hasDefaultDeviceChanged",at=@At("HEAD"),cancellable=true)private void aclab$stable(CallbackInfoReturnable<Boolean> c){if(QaAvCapture.ENABLED)c.setReturnValue(false);}
 @Inject(method="isCurrentDeviceDisconnected",at=@At("HEAD"),cancellable=true)private void aclab$connected(CallbackInfoReturnable<Boolean> c){if(QaAvCapture.ENABLED)c.setReturnValue(false);}
}
