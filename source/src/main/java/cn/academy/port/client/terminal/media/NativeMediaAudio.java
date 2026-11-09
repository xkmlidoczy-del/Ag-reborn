package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.audio.Channel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

/** The source also bridges private sound-engine ownership. No second OpenAL context or separate audio device is created. */
final class NativeMediaAudio {
    private static final Field ENGINE=field(SoundManager.class,"soundEngine"),ACCESS=field(SoundEngine.class,"channelAccess"),LOADED=field(SoundEngine.class,"loaded"),SOURCE=field(Channel.class,"source");
    private NativeMediaAudio() {}
    private static Field field(Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new IllegalStateException("Minecraft 1.21.1 media audio bridge changed: "+name,e);}}
    static ChannelAccess channels(SoundManager manager)throws ReflectiveOperationException,IOException{Object engine=ENGINE.get(manager);if(!LOADED.getBoolean(engine))throw new IOException("Minecraft audio device is unavailable");return(ChannelAccess)ACCESS.get(engine);}
    static void retire(ChannelAccess owner,ChannelAccess.ChannelHandle handle){if(handle==null)return;handle.execute(Channel::stop);if(owner!=null)owner.scheduleTick();}
    static TrackedStream decode(MediaFiles.Track track)throws IOException{
        MediaFiles.requireSource(track.source());var input=Files.newInputStream(track.source());try{return new TrackedStream(new JOrbisAudioStream(input));}catch(IOException|RuntimeException e){input.close();throw e;}
    }
    record Probe(boolean playing,boolean stopped,double seconds,float gain){}
    static Probe probe(Channel channel,TrackedStream stream)throws IllegalAccessException{int source=SOURCE.getInt(channel);return new Probe(channel.playing(),channel.stopped(),stream.seconds(),AL10.alGetSourcef(source,AL10.AL_GAIN));}
    static final class TrackedStream implements AudioStream {
        private final AudioStream delegate;private final MediaQueueTiming timing=new MediaQueueTiming();private int source;private boolean bound;private volatile boolean attached;private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();private volatile IOException failure;
        TrackedStream(AudioStream delegate){this.delegate=delegate;}
        void bind(Channel channel)throws IllegalAccessException{source=SOURCE.getInt(channel);bound=true;}
        void attached(){attached=true;}
        boolean isAttached(){return attached;}
        @Override public AudioFormat getFormat(){return delegate.getFormat();}
        @Override public ByteBuffer read(int bytes)throws IOException{
            try{if(closed.get())throw new IOException("Media stream was closed");if(!bound)throw new IOException("Media audio stream has no native channel");timing.synchronize(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED));ByteBuffer pcm=delegate.read(bytes);if(pcm!=null)timing.queued(pcm.remaining(),getFormat().getFrameSize(),getFormat().getSampleRate());return pcm;}
            catch(IOException e){failure=e;throw e;}catch(RuntimeException e){failure=new IOException("Native media stream failed",e);throw failure;}
        }
        double seconds(){return timing.seconds(AL10.alGetSourcei(source,AL10.AL_BUFFERS_QUEUED),AL10.alGetSourcef(source,AL11.AL_SEC_OFFSET));}
        IOException failure(){return failure;}
        @Override public void close()throws IOException{if(closed.compareAndSet(false,true))delegate.close();}
    }
}
