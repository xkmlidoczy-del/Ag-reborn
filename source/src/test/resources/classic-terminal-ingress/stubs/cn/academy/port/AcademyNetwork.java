package cn.academy.port;
import net.minecraft.nbt.CompoundTag;import net.minecraft.server.level.ServerPlayer;public class AcademyNetwork {public record Request(String action,String value){}public record ClientData(CompoundTag data){}public static int syncs;public static void sync(ServerPlayer p){syncs++;}}
