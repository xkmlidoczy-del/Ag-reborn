package cn.academy.port.terminal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;

/** Actual official modern binary NBT and event bus, without a game/bootstrap or third-party fixtures. */
public final class TerminalStorageRegressionTest {
    private static int checks;
    private static void check(boolean condition,String text){checks++;if(!condition)throw new AssertionError(text);}
    public static void main(String[] arguments)throws Exception{
        Random random=new Random(107252);for(int trace=0;trace<1000;trace++){
            var state=new TerminalState();var installed=new ArrayList<String>();
            for(var app:TerminalState.APPS)if(random.nextBoolean())installed.add(app);
            installed.add("unrecognized_app_"+trace);state.restore(random.nextBoolean(),installed);
            var tag=TerminalStorage.encode(state);var bytes=new ByteArrayOutputStream();try(var output=new DataOutputStream(bytes)){NbtIo.write(tag,output);}
            CompoundTag reloaded;try(var input=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))){reloaded=NbtIo.read(input,NbtAccounter.unlimitedHeap());}
            var restored=TerminalStorage.decode(reloaded);
            check(restored.terminalInstalled()==state.terminalInstalled(),"real binary NBT terminal bit");
            check(restored.savedAppIds().equals(state.savedAppIds()),"real binary NBT all semantic/unknown app IDs");
            check(restored.installedApps().equals(state.installedApps()),"defaults visible independent of terminal bit and saved ledger");
            check(TerminalStorage.encode(restored).equals(tag),"deterministic binary NBT roundtrip");
            check(tag.getInt("schema")==1&&!tag.contains("installedList"),"modern semantic schema never guesses old dynamically ordered bits");
            installed.clear();check(!state.savedAppIds().isEmpty(),"restore detaches input collection");
            boolean immutable=false;try{restored.savedAppIds().add("bad");}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"saved IDs immutable defensive projection");
            check(restored.isInstalled("settings")&&restored.isInstalled("tutorial")&&!restored.isInstalled("unrecognized_app_"+trace),"unknown persisted IDs do not invent executable apps");
        }
        var empty=TerminalStorage.decode(new CompoundTag());check(!empty.terminalInstalled()&&empty.installedApps().equals(List.of("settings","tutorial")),"empty native NBT source defaults");
        check(!ICancellableEvent.class.isAssignableFrom(TerminalInstalledEvent.class)&&!ICancellableEvent.class.isAssignableFrom(AppInstalledEvent.class),"source installed notifications cannot cancel committed installation");
        var bus=BusBuilder.builder().build();var order=new ArrayList<String>();
        bus.addListener(EventPriority.HIGHEST,AppInstalledEvent.class,event->{check(event.app.equals("skill_tree"),"actual event semantic app");order.add("first");});
        bus.addListener(EventPriority.LOWEST,AppInstalledEvent.class,event->order.add("last"));var event=new AppInstalledEvent(null,"skill_tree");
        check(bus.post(event)==event&&order.equals(List.of("first","last")),"actual synchronous native notification bus ordering");
        System.out.println("PASS "+checks+" terminal actual binary NBT/event-bus assertions; no game startup");
    }
}
