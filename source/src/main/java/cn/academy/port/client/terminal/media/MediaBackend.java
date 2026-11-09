package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.world.phys.Vec3;

/** Per-local-player native streaming playback; main-thread state and sound-executor channel mutation. */
public final class MediaBackend {
    private final MediaPlayback playback=new MediaPlayback();
    private MediaSettings settings;private List<MediaFiles.Track> tracks=List.of();private List<String>warnings=List.of();private boolean scanning;
    private ChannelAccess channelOwner;private ChannelAccess.ChannelHandle handle;private NativeMediaAudio.TrackedStream stream,pendingDecoder;private volatile long activeToken;private volatile float volume=1;
    private String error="";private int ticks;private SourceStamp sourceStamp;
    private static final MediaBackend INSTANCE=new MediaBackend();
    public static MediaBackend instance(){return INSTANCE;}
    private MediaBackend(){}
    public List<MediaFiles.Track> tracks(){return tracks;}public List<String>warnings(){return warnings;}public String error(){return error;}public boolean scanning(){return scanning;}
    public String name(MediaFiles.Track t){return settings==null?t.id():settings.name(t.id());}public String description(MediaFiles.Track t){return settings==null?t.id():settings.description(t.id());}
    public MediaPlayback.Info current(){return playback.current();}public boolean loading(){return playback.loading();}public float volume(){return volume;}
    public void refresh(){
        if(scanning)return;scanning=true;error="";Minecraft mc=Minecraft.getInstance();
        CompletableFuture.supplyAsync(()->{try{var scan=MediaFiles.scan(mc.gameDirectory.toPath());var valid=new ArrayList<MediaFiles.Track>();var notes=new ArrayList<String>(scan.warnings());for(var track:scan.tracks()){try(var decoder=NativeMediaAudio.decode(track)){valid.add(track);}catch(IOException|RuntimeException problem){notes.add(track.id()+": "+problem.getMessage());}}return new LibraryScan(new MediaFiles.Scan(List.copyOf(valid),List.copyOf(notes)),new MediaSettings(mc.gameDirectory.toPath()));}catch(IOException e){throw new java.util.concurrent.CompletionException(e);}},Util.nonCriticalIoPool()).whenComplete((scan,failure)->mc.execute(()->{
            scanning=false;if(failure!=null){report("Couldn't read acmedia",failure);return;}tracks=scan.files.tracks();warnings=scan.files.warnings();if(settings==null){settings=scan.settings;volume=settings.volume();applyVolume();}
        }));
    }
    private record LibraryScan(MediaFiles.Scan files,MediaSettings settings){}
    private record SourceStamp(long size,java.nio.file.attribute.FileTime modified,Object key){static SourceStamp read(MediaFiles.Track media)throws IOException{MediaFiles.requireSource(media.source());var a=Files.readAttributes(media.source(),BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);return new SourceStamp(a.size(),a.lastModifiedTime(),a.fileKey());}}
    public void play(MediaFiles.Track media){
        stopCurrent();try{sourceStamp=SourceStamp.read(media);}catch(IOException e){report("Local media source is unavailable",e);return;}Minecraft mc=Minecraft.getInstance();long token=playback.request(media);activeToken=token;error="";
        CompletableFuture.supplyAsync(()->{try{return NativeMediaAudio.decode(media);}catch(IOException|RuntimeException e){throw new java.util.concurrent.CompletionException(e);}},Util.nonCriticalIoPool()).whenComplete((decoded,failure)->mc.execute(()->{
            if(!playback.accepts(token)){close(decoded);return;}if(failure!=null){playback.failed(token);report("Couldn't decode "+media.id(),failure);return;}
            pendingDecoder=decoded;try{ChannelAccess owner=NativeMediaAudio.channels(mc.getSoundManager());owner.createHandle(Library.Pool.STREAMING).whenComplete((channel,problem)->mc.execute(()->{
                if(!playback.accepts(token)){close(decoded);NativeMediaAudio.retire(owner,channel);return;}
                if(problem!=null||channel==null){close(decoded);pendingDecoder=null;playback.failed(token);report("No Minecraft streaming audio channel is available",problem);return;}
                channelOwner=owner;handle=channel;stream=decoded;pendingDecoder=null;
                channel.execute(nativeChannel->{
                    if(activeToken!=token){close(decoded);nativeChannel.stop();owner.scheduleTick();return;}
                    try{
                        decoded.bind(nativeChannel);nativeChannel.setRelative(true);nativeChannel.setSelfPosition(Vec3.ZERO);nativeChannel.disableAttenuation();nativeChannel.setLooping(false);nativeChannel.setPitch(1);nativeChannel.setVolume(volume);decoded.attached();nativeChannel.attachBufferStream(decoded);
                        if(decoded.failure()!=null)throw decoded.failure();nativeChannel.play();
                        mc.execute(()->{if(playback.accepts(token)){playback.started(token);if(playback.desiredPause())channel.execute(Channel::pause);}});
                    }catch(Exception e){nativeChannel.stop();owner.scheduleTick();close(decoded);mc.execute(()->{if(playback.accepts(token)){playback.failed(token);handle=null;stream=null;channelOwner=null;report("Couldn't start native media audio",e);}});}
                });
            }));}catch(ReflectiveOperationException|IOException|RuntimeException e){close(decoded);pendingDecoder=null;playback.failed(token);report("Minecraft media audio is unavailable",e);}
        }));
    }
    public void togglePlayPause(){var current=playback.current();if(current!=null){if(current.paused())resumeCurrent();else pauseCurrent();}else if(playback.loading()){if(playback.desiredPause())resumeCurrent();else pauseCurrent();}else if(playback.last()!=null)play(playback.last());else if(!tracks.isEmpty())play(tracks.getFirst());}
    public void pauseCurrent(){playback.pause();if(handle!=null)handle.execute(Channel::pause);}
    public void resumeCurrent(){playback.resume();if(handle!=null)handle.execute(Channel::unpause);}
    public void stopCurrent(){playback.stop();activeToken++;sourceStamp=null;close(pendingDecoder);pendingDecoder=null;if(stream!=null&&!stream.isAttached())close(stream);NativeMediaAudio.retire(channelOwner,handle);handle=null;stream=null;channelOwner=null;}
    public void resetPlayer(){stopCurrent();playback.reset();activeToken++;}
    public void audioReloaded(){stopCurrent();}
    ChannelAccess.ChannelHandle currentHandle(){return handle;}
    CompletableFuture<NativeMediaAudio.Probe> probe(){var answer=new CompletableFuture<NativeMediaAudio.Probe>();var h=handle;var s=stream;if(h==null||s==null||h.isStopped()){answer.complete(null);return answer;}h.execute(ch->{try{answer.complete(NativeMediaAudio.probe(ch,s));}catch(Exception e){answer.completeExceptionally(e);}});return answer;}
    public void volume(float value){volume=MediaSettings.clamp(value);applyVolume();if(settings!=null)try{settings.volume(volume);}catch(IOException e){report("Couldn't save media volume",e);}}
    private void applyVolume(){if(handle!=null)handle.execute(ch->ch.setVolume(volume));}
    public void edit(MediaFiles.Track track,boolean name,String text){if(settings==null)return;try{if(name)settings.name(track.id(),text);else settings.description(track.id(),text);}catch(IOException e){report("Couldn't save media metadata",e);}}
    public void tick(){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null){if(playback.current()!=null||playback.loading())stopCurrent();return;}
        if(++ticks%5!=0)return;var info=playback.current();if(info!=null)try{if(!Objects.equals(sourceStamp,SourceStamp.read(info.media()))){stopCurrent();error="Local media source changed; refresh and replay the track";return;}}catch(IOException e){stopCurrent();report("Local media source changed or was removed",e);return;}if(info!=null&&!info.paused())mc.getMusicManager().stopPlaying();
        var observing=handle;var audio=stream;long token=activeToken;if(observing==null||audio==null)return;
        if(observing.isStopped()){if(!audio.isAttached())close(audio);playback.failed(token);handle=null;stream=null;channelOwner=null;return;}
        observing.execute(ch->{try{boolean stopped=ch.stopped(),playing=ch.playing();double seconds=stopped?0:audio.seconds();IOException failure=audio.failure();mc.execute(()->{if(!playback.accepts(token))return;if(failure!=null){stopCurrent();report("Media stream decode failed",failure);}else{playback.observe(token,playing,stopped,seconds);if(stopped){handle=null;stream=null;channelOwner=null;}}});}catch(RuntimeException e){mc.execute(()->{if(playback.accepts(token)){stopCurrent();report("Native audio device changed; replay the track",e);}});}});
    }
    private void report(String message,Throwable failure){error=message;if(failure!=null){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();error+=": "+cause.getMessage();LogUtils.getLogger().warn(message,failure);}}
    private static void close(NativeMediaAudio.TrackedStream value){if(value!=null)try{value.close();}catch(IOException ignored){}}
}
