package cn.academy.port.client.terminal.media;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Finite opt-in client acceptance fixture. It requires a real local player/audio device and an already authorized,
 * user-provided Vorbis file under that client's acmedia/source. It never synthesizes or redistributes a song.
 * Compiled in this isolated stage; NOT executed by the worker or the headless test main.
 */
public final class MediaNativePlaybackFixture {
    public record Report(String source,int ticks,boolean nativePlaying,boolean nativePaused,boolean nativeResumed,boolean gainQuarter,boolean channelReleased){}
    private static Session session;
    private static final java.util.function.Consumer<ClientTickEvent.Post> LISTENER=MediaNativePlaybackFixture::tick;
    private MediaNativePlaybackFixture(){}
    public static CompletableFuture<Report> start(Path source){
        if(session!=null)throw new IllegalStateException("A native media fixture is already running");if(Minecraft.getInstance().player==null)throw new IllegalStateException("A real client player is required");
        var created=new Session(source.toAbsolutePath().normalize());session=created;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(LISTENER);MediaBackend.instance().refresh();return created.answer;
    }
    public static void tick(ClientTickEvent.Post event){if(session!=null)session.tick();}
    private static final class Session {
        final Path source;final CompletableFuture<Report>answer=new CompletableFuture<>();final float originalVolume=MediaBackend.instance().volume();int ticks,phase,pausedAt;double frozen;boolean pending;ChannelAccess.ChannelHandle channel;
        Session(Path source){this.source=source;}
        void tick(){if(++ticks>240){fail(new AssertionError("Native media fixture timed out after 240 client ticks"));return;}var backend=MediaBackend.instance();
            if(phase==0){if(backend.scanning())return;var track=backend.tracks().stream().filter(t->t.source().toAbsolutePath().normalize().equals(source)).findFirst();if(track.isEmpty()){fail(new AssertionError("Fixture user source was not discovered: "+source+" "+backend.warnings()));return;}backend.play(track.get());phase=1;return;}
            if(phase==7){if(channel!=null&&channel.isStopped()){backend.volume(originalVolume);session=null;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(LISTENER);answer.complete(new Report(source.toString(),ticks,true,true,true,true,true));}return;}
            if(pending)return;pending=true;backend.probe().whenComplete((probe,error)->Minecraft.getInstance().execute(()->{pending=false;if(session!=this)return;if(error!=null){fail(error);return;}if(probe==null)return;
                try{switch(phase){
                    case 1->{if(!probe.playing()||probe.seconds()<=0)return;channel=backend.currentHandle();backend.volume(.25f);phase=2;}
                    case 2->{if(Math.abs(probe.gain()-.25f)>.001f)throw new AssertionError("Native AL_GAIN is not .25");backend.pauseCurrent();phase=3;}
                    case 3->{if(probe.playing()||probe.stopped())return;frozen=probe.seconds();pausedAt=ticks;phase=4;}
                    case 4->{if(Math.abs(probe.seconds()-frozen)>.05)throw new AssertionError("Native paused source advanced");if(ticks-pausedAt>=10){backend.resumeCurrent();phase=5;}}
                    case 5->{if(!probe.playing()||probe.seconds()<=frozen+.05)return;backend.pauseCurrent();phase=6;}
                    case 6->{if(probe.playing()||probe.stopped())return;backend.stopCurrent();phase=7;}
                    default->{}
                }}catch(Throwable problem){fail(problem);}
            }));
        }
        void fail(Throwable error){var backend=MediaBackend.instance();backend.stopCurrent();backend.volume(originalVolume);session=null;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(LISTENER);answer.completeExceptionally(error);}
    }
}
