package cn.academy.port.fleet.client;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import cn.academy.port.fleet.InteractionLab;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.SOFTLoopback;
/** QA-only: actual OpenAL mixed samples plus timestamped framebuffer captures; no microphone/system audio. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class QaAvCapture {
 public static final int RATE=48000;public static final boolean ENABLED=Boolean.getBoolean("aclab.qa.captureAudio")&&Boolean.getBoolean("academy.qa.confirmOwnWorld");
 private static long device,lastTime,origin,nextFrame,samples;private static FileChannel pcm;private static PrintWriter frames,events;private static Path directory;private static int frameNumber,dropped;private static volatile Throwable failure;private static boolean driverView;
 private static final ByteBuffer chunk=BufferUtils.createByteBuffer(4800*8).order(ByteOrder.LITTLE_ENDIAN);
 private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(1,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(3),r->{var t=new Thread(r,"ACA-AV-PNG");t.setDaemon(true);return t;});
 public static void attach(long value){device=value;lastTime=System.nanoTime();InteractionLab.LOGGER.info("ACLAB_AV_LOOPBACK device={} rate={} stereo=float32",device,RATE);}
 public static void detach(){if(device!=0){pump(System.nanoTime());stop();device=0;}}
 public static synchronized void note(String type,JsonObject data){if(events!=null){data.addProperty("type",type);data.addProperty("seconds",(System.nanoTime()-origin)/1e9);events.println(data);}}
 // At SoundEngine.play HEAD the random Sound has not been resolved yet: getVolume/getPitch are unsafe here.
 public static void sound(net.minecraft.client.resources.sounds.SoundInstance s){if(!ENABLED||!s.getLocation().getNamespace().equals("academy"))return;var j=new JsonObject();j.addProperty("event",s.getLocation().toString());j.addProperty("x",s.getX());j.addProperty("y",s.getY());j.addProperty("z",s.getZ());j.addProperty("game_tick",Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime());note("sound_request",j);}
 private static void pump(long now){if(device==0)return;long due;if(pcm!=null)due=(long)((now-origin)*(RATE/1e9))-samples;else due=Math.min(RATE/4,(long)((now-lastTime)*(RATE/1e9)));lastTime=now;while(due>0){int n=(int)Math.min(4800,due);chunk.clear();chunk.limit(n*8);SOFTLoopback.alcRenderSamplesSOFT(device,chunk.asFloatBuffer(),n);if(pcm!=null){try{while(chunk.hasRemaining())pcm.write(chunk);}catch(IOException ex){throw new IllegalStateException(ex);}samples+=n;}due-=n;}}
 private static void start(String name){if(!ENABLED||device==0)throw new IllegalStateException("OpenAL loopback capture unavailable");if(pcm!=null)throw new IllegalStateException("Already recording");if(!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Capture name");pump(System.nanoTime());directory=Minecraft.getInstance().gameDirectory.toPath().resolve("av").resolve(name);if(Files.exists(directory))throw new IllegalStateException("Capture name already exists");try{Files.createDirectories(directory.resolve("frames"));pcm=FileChannel.open(directory.resolve("game_mix.f32le"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);frames=new PrintWriter(Files.newBufferedWriter(directory.resolve("frames.csv")));events=new PrintWriter(Files.newBufferedWriter(directory.resolve("events.jsonl")));}catch(IOException ex){throw new IllegalStateException(ex);}frames.println("index,seconds,game_tick,partial");frameNumber=dropped=0;samples=0;failure=null;origin=lastTime=nextFrame=System.nanoTime();InteractionLab.LOGGER.info("ACLAB_AV_START {}",name);}
 private static void stop(){if(pcm==null)return;long end=System.nanoTime();pump(end);try{while(writer.getActiveCount()>0||!writer.getQueue().isEmpty())Thread.sleep(4);pcm.close();frames.close();events.close();var j=new JsonObject();j.addProperty("sample_rate",RATE);j.addProperty("channels",2);j.addProperty("format","float32le");j.addProperty("samples",samples);j.addProperty("duration_seconds",samples/(double)RATE);j.addProperty("video_frames",frameNumber);j.addProperty("dropped_frames",dropped);j.addProperty("capture","Minecraft OpenAL Soft ALC_SOFT_loopback mixer output, shared monotonic clock with framebuffer PTS");Files.writeString(directory.resolve("capture.json"),new GsonBuilder().setPrettyPrinting().create().toJson(j));InteractionLab.LOGGER.info("ACLAB_AV_STOP {} samples={} frames={} drops={}",directory,samples,frameNumber,dropped);}catch(Exception ex){throw new IllegalStateException(ex);}finally{pcm=null;frames=events=null;}if(failure!=null)throw new IllegalStateException("PNG writer failed",failure);}
 @SubscribeEvent public static void register(RegisterClientCommandsEvent e){if(!ENABLED)return;e.getDispatcher().register(Commands.literal("aclab_qa_av").then(Commands.literal("start").then(Commands.argument("name",StringArgumentType.word()).executes(c->{start(StringArgumentType.getString(c,"name"));return 1;}))).then(Commands.literal("stop").executes(c->{stop();return 1;})).then(Commands.literal("driver_view").then(Commands.argument("enabled",BoolArgumentType.bool()).executes(c->{driverView=BoolArgumentType.getBool(c,"enabled");return 1;}))));}
 // Explicit filming option: keep the real camera facing the moving vehicle's heading, before Camera.setup.
 @SubscribeEvent public static void driverView(ClientTickEvent.Post e){if(!ENABLED||!driverView)return;var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.getVehicle() instanceof cn.academy.port.fleet.LabVehicle v){mc.player.setYRot(v.getYRot());mc.player.yRotO=v.getYRot();mc.player.setXRot(8);mc.player.xRotO=8;}}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(!ENABLED||device==0)return;long now=System.nanoTime();pump(now);if(pcm==null||now<nextFrame)return;nextFrame=now+50_000_000L;if(writer.getQueue().remainingCapacity()==0){dropped++;return;}var mc=Minecraft.getInstance();var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());int n=frameNumber++;double t=(now-origin)/1e9;frames.printf(Locale.ROOT,"%d,%.9f,%d,%.6f%n",n,t,mc.level==null?0:mc.level.getGameTime(),mc.getTimer().getGameTimeDeltaPartialTick(false));Path target=directory.resolve("frames").resolve(String.format(Locale.ROOT,"f%06d.png",n));writer.execute(()->{try(image){image.writeToFile(target);}catch(Throwable ex){failure=ex;}});}
}
