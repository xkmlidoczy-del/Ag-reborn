package cn.academy.port.client.terminal.media;

import java.lang.reflect.Modifier;

/** Official API/bridge shape check without class initialization, OpenAL calls, player creation or native launch. */
public final class MediaNativeApiShapeTest {
    public static void main(String[]args)throws Exception{
        ClassLoader loader=MediaNativeApiShapeTest.class.getClassLoader();Class<?> manager=Class.forName("net.minecraft.client.sounds.SoundManager",false,loader),engine=Class.forName("net.minecraft.client.sounds.SoundEngine",false,loader),channel=Class.forName("com.mojang.blaze3d.audio.Channel",false,loader),access=Class.forName("net.minecraft.client.sounds.ChannelAccess",false,loader),decoder=Class.forName("net.minecraft.client.sounds.JOrbisAudioStream",false,loader);
        if(manager.getDeclaredField("soundEngine").getType()!=engine||engine.getDeclaredField("channelAccess").getType()!=access||engine.getDeclaredField("loaded").getType()!=boolean.class||channel.getDeclaredField("source").getType()!=int.class)throw new AssertionError("Pinned Minecraft audio bridge fields changed");
        decoder.getConstructor(java.io.InputStream.class);for(String name:new String[]{"play","pause","unpause","stop","playing","stopped","updateStream","disableAttenuation"})if(!Modifier.isPublic(channel.getMethod(name).getModifiers()))throw new AssertionError("Missing native channel API "+name);
        System.out.println("PASS official MC1.21.1/NeoForge21.1.252 audio decoder, native channel and private ownership field shapes; no classes initialized or audio launched");
    }
}
