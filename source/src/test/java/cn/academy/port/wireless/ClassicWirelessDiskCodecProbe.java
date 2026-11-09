package cn.academy.port.wireless;

import cn.academy.port.wireless.ClassicWirelessGraph.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Two ordinary Java processes; actual compressed-NBT disk reload, no Minecraft runtime. */
public final class ClassicWirelessDiskCodecProbe {
    private static State expected() {
        return new State(List.of(new NetworkData(new Pos(-16,70,32),"disk-fixture","game-only-password",.625,List.of(new Pos(0,70,32),new Pos(10,70,32)))),List.of(new ConnectionData(new Pos(0,70,32),List.of(new Pos(1,70,32)),List.of(new Pos(2,70,32)))));
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("phase and stage-owned file required");var file=Path.of(args[1]);
        if(args[0].equals("seed")){NbtIo.writeCompressed(ClassicWirelessSavedData.writeState(new CompoundTag(),expected()),file);if(Files.size(file)==0)throw new AssertionError("empty file");System.out.println("ClassicWirelessDiskCodecProbe: compressed NBT seed written");}
        else if(args[0].equals("verify")){var state=ClassicWirelessSavedData.readState(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()));if(!state.equals(expected()))throw new AssertionError("new JVM compressed native NBT state mismatch");System.out.println("ClassicWirelessDiskCodecProbe: fresh JVM compressed NBT reload passed");}
        else throw new IllegalArgumentException("phase must be seed or verify");
    }
}
