package cn.academy.port.worldboss;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authored visual cues; no client class is linked by the common registration. */
public record FxPacket(String kind, float x, float y, float z, float a, float b, float c, int i, int j)
        implements CustomPacketPayload {
    public static volatile Consumer<FxPacket> CLIENT_HANDLER = packet -> {};
    public static final Type<FxPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "world_boss_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FxPacket> CODEC = StreamCodec.of((buf,p) -> {
        buf.writeUtf(p.kind,32); buf.writeFloat(p.x); buf.writeFloat(p.y); buf.writeFloat(p.z);
        buf.writeFloat(p.a); buf.writeFloat(p.b); buf.writeFloat(p.c); buf.writeInt(p.i); buf.writeInt(p.j);
    }, buf -> new FxPacket(buf.readUtf(32),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readInt(),buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
