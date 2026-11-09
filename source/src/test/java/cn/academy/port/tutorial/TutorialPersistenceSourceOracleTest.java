package cn.academy.port.tutorial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import static cn.academy.port.tutorial.TutorialOracleAssertions.*;

/** Real modern NBT binary round-trip compared with the four original annotated TutorialData fields.
 * The finite classic boundary copies field values; classic NBTS11n byte encoding is not executed or claimed. */
public final class TutorialPersistenceSourceOracleTest {
    private static CompoundTag binaryRoundTrip(CompoundTag tag) throws Exception {
        var bytes=new ByteArrayOutputStream();
        NbtIo.writeCompressed(tag,bytes);
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.unlimitedHeap());
    }
    @SuppressWarnings("unchecked") private static CompoundTag comparePersistence(TutorialState runtime,ClassicTutorialOracle source,String label) throws Exception {
        var fields=source.save();
        equal(fields.keySet(),Set.of("savedConditions","activatedTuts","tutorialAcquired","misakaID"),label+" exact original annotated field set");
        var tag=binaryRoundTrip(TutorialStorage.encode(runtime));
        equal(tag.getLongArray("savedConditions"),((BitSet)fields.get("savedConditions")).toLongArray(),label+" actual NBT bitset equals original persisted field");
        var ids=new HashSet<String>();
        for(var value:tag.getList("activatedTuts",Tag.TAG_STRING))ids.add(value.getAsString());
        equal(ids,(Set<String>)fields.get("activatedTuts"),label+" actual NBT activation membership equals original persisted field");
        equal(tag.getBoolean("tutorialAcquired"),fields.get("tutorialAcquired"),label+" actual NBT guide acquisition equals original persisted field");
        equal(tag.getInt("misakaID"),fields.get("misakaID"),label+" actual NBT Misaka ID equals original persisted field");
        equal(tag.getBoolean("AC_Tutorial_Open"),runtime.firstOpened(),label+" separate modern guide-open extension round-trip");
        equal(tag.getInt("schema"),1,label+" explicit modern NBT schema");
        return tag;
    }
    public static void main(String[] args) throws Exception {
        try(var source=new ClassicTutorialOracle(true)) {
            var runtime=new TutorialState(source.misakaID(),true);
            var trace=new ArrayList<String>();
            var random=new Random(107151L);
            for(int tick=1;tick<=150;tick++) {
                if(random.nextInt(3)!=0) {
                    String item=ClassicTutorials.CONDITION_ITEMS.get(random.nextInt(21));
                    var event=TutorialState.EventKind.values()[random.nextInt(3)];
                    equal(runtime.recordObtained(item,event),source.record(classic(item),event),"persisted source event "+tick);
                }
                if(tick==17)runtime.markOpened();
                if(tick%7==0) {
                    var tag=comparePersistence(runtime,source,"pre-tick field snapshot "+tick);
                    source.restart();
                    runtime=TutorialStorage.decode(tag,tag.getInt("misakaID"),true);
                    compareState(runtime,source,"NBT restore with original transient reset "+tick);
                    equal(runtime.firstOpened(),tick>=17,"guide-open extension survives actual NBT bytes");
                }
                tick(runtime,source,trace,"NBT-bound differential tick "+tick);
            }
            comparePersistence(runtime,source,"final native NBT fields");
        }
        for(boolean acquired:List.of(false,true)) {
            try(var source=new ClassicTutorialOracle(false)) {
                var runtime=new TutorialState(source.misakaID(),false);
                var bits=new BitSet();for(int bit:List.of(0,62,63,64,127,4096))bits.set(bit);
                var ids=List.of("ores","welcome","future_extension","terminal");
                source.restore(bits.toLongArray(),ids,acquired);
                runtime.restore(bits.toLongArray(),ids,acquired,true);
                var tag=comparePersistence(runtime,source,"arbitrary source persistence fields "+acquired);
                var restored=TutorialStorage.decode(tag);
                compareState(restored,source,"arbitrary native NBT binary restore "+acquired);
                check(restored.firstOpened(),"arbitrary native NBT guide-open extension preserved");
                var trace=new ArrayList<String>();
                for(int tick=1;tick<=30;tick++)tick(restored,source,trace,"restored raw native NBT tick "+tick);
                equal(trace,List.of(),"raw persisted fields do not replay activation or grant");
            }
        }
        System.out.println("PASS "+checks+" original persisted-field / actual compressed native NBT differential checks");
    }
}
