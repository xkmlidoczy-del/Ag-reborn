package cn.aca.infinity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GauntletData(int stonesMask, int selectedOrdinal) {
    public static final GauntletData EMPTY = new GauntletData(0, 0);
    public static final GauntletData FULL = new GauntletData(Stone.ALL_MASK, 0);

    public static final Codec<GauntletData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("stones").forGetter(GauntletData::stonesMask),
            Codec.INT.fieldOf("selected").forGetter(GauntletData::selectedOrdinal)
    ).apply(inst, GauntletData::new));

    public static final StreamCodec<ByteBuf, GauntletData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GauntletData::stonesMask,
            ByteBufCodecs.VAR_INT, GauntletData::selectedOrdinal,
            GauntletData::new
    );

    public GauntletData {
        stonesMask &= Stone.ALL_MASK;
        selectedOrdinal = Math.floorMod(selectedOrdinal, Stone.values().length);
    }

    public boolean has(Stone stone) {
        return (stonesMask & stone.bit()) != 0;
    }

    public boolean hasAllStones() {
        return (stonesMask & Stone.ALL_MASK) == Stone.ALL_MASK;
    }

    public int count() {
        return Integer.bitCount(stonesMask & Stone.ALL_MASK);
    }

    public Stone selected() {
        return Stone.byOrdinal(selectedOrdinal);
    }

    public GauntletData withStone(Stone stone) {
        int nextMask = stonesMask | stone.bit();
        int nextSel = stonesMask == 0 ? stone.ordinal() : selectedOrdinal;
        return new GauntletData(nextMask, nextSel);
    }

    public GauntletData withoutStone(Stone stone) {
        int nextMask = stonesMask & ~stone.bit();
        int nextSel = selectedOrdinal;
        if (nextSel == stone.ordinal() && nextMask != 0) {
            for (Stone s : Stone.values()) {
                if ((nextMask & s.bit()) != 0) {
                    nextSel = s.ordinal();
                    break;
                }
            }
        }
        return new GauntletData(nextMask, nextSel);
    }

    public GauntletData withSelected(Stone stone) {
        return new GauntletData(stonesMask, stone.ordinal());
    }
}
