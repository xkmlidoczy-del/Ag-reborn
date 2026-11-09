package cn.academy.port.city;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Choices contain identifiers only. Services, prices and quest transitions are owned by the server. */
public final class CityDialogueNetwork {
    private CityDialogueNetwork() {}
    public static Consumer<View> clientReceiver = view -> {};
    public record Option(String id, Component label, boolean enabled) {}
    public record View(int entity, UUID npc, long token, Component title, Component body, int coins, int district,
                       List<Option> options) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "city_dialogue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.entity); b.writeUUID(p.npc); b.writeLong(p.token);
            ComponentSerialization.STREAM_CODEC.encode(b,p.title); ComponentSerialization.STREAM_CODEC.encode(b,p.body);
            b.writeVarInt(p.coins); b.writeVarInt(p.district); b.writeVarInt(p.options.size());
            for (var o:p.options) { b.writeUtf(o.id,64); ComponentSerialization.STREAM_CODEC.encode(b,o.label); b.writeBoolean(o.enabled); }
        }, b -> {
            int entity=b.readVarInt(); UUID npc=b.readUUID(); long token=b.readLong();
            var title=ComponentSerialization.STREAM_CODEC.decode(b); var body=ComponentSerialization.STREAM_CODEC.decode(b);
            int coins=b.readVarInt(), district=b.readVarInt(), count=b.readVarInt();
            if (count<0 || count>32) throw new IllegalArgumentException("Invalid city dialogue option count");
            var options=new ArrayList<Option>(count);
            for(int i=0;i<count;i++) options.add(new Option(b.readUtf(64),ComponentSerialization.STREAM_CODEC.decode(b),b.readBoolean()));
            return new View(entity,npc,token,title,body,coins,district,List.copyOf(options));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Choice(UUID npc, long token, String action) implements CustomPacketPayload {
        public static final Type<Choice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "city_dialogue_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Choice> CODEC = StreamCodec.of((b,p) -> {
            b.writeUUID(p.npc); b.writeLong(p.token); b.writeUtf(p.action,64);
        }, b -> new Choice(b.readUUID(),b.readLong(),b.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid="academy", bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            var r=event.registrar("1");
            r.playToClient(View.TYPE,View.CODEC,(p,ctx) -> clientReceiver.accept(p));
            r.playToServer(Choice.TYPE,Choice.CODEC,(p,ctx) -> {
                if(ctx.player() instanceof ServerPlayer player) CityDialogue.choose(player,p);
            });
        }
    }
}
