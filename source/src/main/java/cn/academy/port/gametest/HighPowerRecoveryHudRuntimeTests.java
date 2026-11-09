package cn.academy.port.gametest;

import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native codec checks; these are fixtures, never client-to-server messages or gameplay mutations. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRecoveryHudRuntimeTests {
    private HighPowerRecoveryHudRuntimeTests() {}
    @GameTest(template = "highpower_empty", batch = "highpower_recovery_hud", timeoutTicks = 10)
    public static void md_curtain_half_reserve_v4_roundtrip(GameTestHelper helper) {
        var identity = new HighPowerRecoveryHudModel.Identity(UUID.randomUUID(),17,"minecraft:overworld",2,7);
        var values = new HighPowerRecoveryHudModel.Values("1","100","7","20","full","0",0,0,0,true,
                "0","0",false,"meltdowner","9.999999999999999999","10",true);
        var packet = new HighPowerRecoveryHudPayload.Snapshot(identity,values);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),helper.getLevel().registryAccess());
        try {
            HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(buffer,packet);
            var decoded=HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer);
            helper.assertTrue(packet.equals(decoded)&&!buffer.isReadable()&&decoded.values().curtainVisible()
                    &&!decoded.values().domainVisible(),"v4 preserves exact MD-only lower reserve, never EM upper visibility");
        } finally { buffer.release(); }
        helper.succeed();
    }
    @GameTest(template = "highpower_empty", batch = "highpower_recovery_hud", timeoutTicks = 10)
    public static void exact_decimal_payload_and_clear_roundtrip(GameTestHelper helper) {
        var identity = new HighPowerRecoveryHudModel.Identity(UUID.randomUUID(), 17, "minecraft:overworld", 2, 7);
        var values = new HighPowerRecoveryHudModel.Values("99999999999999999999.125", "100000000000000000000",
                "0.00000000000000000001", "100000000000000000000", "saturated", "200000000000000000000", 10, 1, 0, true);
        for (var packet : new HighPowerRecoveryHudPayload.Snapshot[]{new HighPowerRecoveryHudPayload.Snapshot(identity, values),
                new HighPowerRecoveryHudPayload.Snapshot(identity, null)}) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(buffer, packet);
                helper.assertTrue(buffer.readableBytes() < 8192, "bounded own-player HUD packet stays below eight KiB");
                var decoded = HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer);
                helper.assertTrue(packet.equals(decoded) && !buffer.isReadable(), "all exact strings, identity and optional clear roundtrip without float conversion");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }
    @GameTest(template = "highpower_empty", batch = "highpower_recovery_hud", timeoutTicks = 10)
    public static void bounded_decoder_rejects_oversize_and_exponent_expansion(GameTestHelper helper) {
        for (String malformed : new String[]{"1".repeat(513), "1E999999999", "NaN", "-1"}) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                buffer.writeUUID(UUID.randomUUID()); buffer.writeInt(1); buffer.writeUtf("minecraft:overworld", 128);
                buffer.writeLong(1); buffer.writeLong(1); buffer.writeBoolean(true);
                buffer.writeUtf(malformed); buffer.writeUtf("20"); buffer.writeUtf("0"); buffer.writeUtf("0");
                buffer.writeUtf("full"); buffer.writeUtf("0"); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeBoolean(false);
                boolean refused = false;
                try { HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer); }
                catch (RuntimeException invalid) { refused = true; }
                helper.assertTrue(refused, "bounded decode refuses malformed health before any display or state application");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }
}
