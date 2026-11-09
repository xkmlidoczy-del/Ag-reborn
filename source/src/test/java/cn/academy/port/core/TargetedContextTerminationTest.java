package cn.academy.port.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executable engine-free transport/identity tests. Does not boot a game or emulate gameplay validity. */
public final class TargetedContextTerminationTest {
    private static int checks;
    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (var skill : TargetedContextTermination.Skill.values()) {
            for (long input : new long[]{1, 42, Long.MAX_VALUE}) for (long token : new long[]{1, 137, Long.MAX_VALUE}) {
                var request = new TargetedContextTermination.Request(skill, input, token);
                check(request.equals(TargetedContextTermination.parse(request.wire())), "canonical roundtrip " + request);
                check(request.wire().length() <= 64, "compatible payload bound");
            }
        }
        for (String id : List.of("vec_deviation", "vec_reflection", "flashing", "storm_wing")) {
            for (String bad : List.of("", "0", "00", "01", "-1", "+1", " 1", "1 ", "1.0", "1e1", "\n1", "١", "１", "9223372036854775808", "9999999999999999999", "10000000000000000000")) {
                check(TargetedContextTermination.parse(id + ":" + bad + ":2") == null, "reject input " + bad);
                check(TargetedContextTermination.parse(id + ":2:" + bad) == null, "reject token " + bad);
            }
        }
        for (String bad : List.of("", "flashing", "flashing:1", "flashing:1:2:3", "flashing::2", "flashing:1:", ":1:2", "Flashing:1:2", "railgun:1:2", "plasma_cannon:1:2", "flashing :1:2", "storm_wing:1:2\n", "flashing:1:2" + "x".repeat(65)))
            check(TargetedContextTermination.parse(bad) == null, "malformed/whitelist bound " + bad);
        check(TargetedContextTermination.parse(null) == null, "null wire");
        for (long number : new long[]{0, -1, Long.MIN_VALUE}) {
            boolean inputThrew = false, tokenThrew = false;
            try { new TargetedContextTermination.Request(TargetedContextTermination.Skill.FLASHING, number, 1); } catch (IllegalArgumentException expected) { inputThrew = true; }
            try { new TargetedContextTermination.Request(TargetedContextTermination.Skill.FLASHING, 1, number); } catch (IllegalArgumentException expected) { tokenThrew = true; }
            check(inputThrew && tokenThrew, "constructor excludes nonce/token wildcard");
        }

        Object owner = new Object(), level = new Object(), state = new Object();
        var bound = new TargetedContextTermination.Binding(owner, level, state, 11, 91);
        check(TargetedContextTermination.matches(bound, owner, level, state, 11, 91), "exact session");
        check(!TargetedContextTermination.matches(bound, new Object(), level, state, 11, 91), "same UUID replacement must carry distinct object");
        check(!TargetedContextTermination.matches(bound, owner, new Object(), state, 11, 91), "dimension Level replacement");
        check(!TargetedContextTermination.matches(bound, owner, level, new Object(), 11, 91), "ability state/cache replacement");
        check(!TargetedContextTermination.matches(bound, owner, level, state, 12, 91), "input is not server token");
        check(!TargetedContextTermination.matches(bound, owner, level, state, 11, 92), "old accepted server context token");
        check(!TargetedContextTermination.matches(bound, owner, level, state, 91, 11), "nonce/token swap rejected");
        check(!TargetedContextTermination.matches(bound, owner, level, state, 0, 91), "no nonce wildcard");
        check(!TargetedContextTermination.matches(bound, owner, level, state, 11, 0), "no pending token wildcard");
        check(!TargetedContextTermination.matches(null, owner, level, state, 11, 91), "no live Hold");
        check(!TargetedContextTermination.matches(bound, null, level, state, 11, 91), "null current owner");
        check(!TargetedContextTermination.matches(bound, owner, null, state, 11, 91), "null current Level");
        check(!TargetedContextTermination.matches(bound, owner, level, null, 11, 91), "null current state");

        // Context identities survive an unrelated preset/group rebuild. The policy never consults a slot.
        Map<TargetedContextTermination.Skill, TargetedContextTermination.Binding> contexts = new LinkedHashMap<>();
        contexts.put(TargetedContextTermination.Skill.VEC_DEVIATION, bound);
        contexts.put(TargetedContextTermination.Skill.VEC_REFLECTION, new TargetedContextTermination.Binding(owner, level, state, 12, 92));
        var selected = TargetedContextTermination.parse("vec_reflection:12:92");
        check(selected != null && TargetedContextTermination.matches(contexts.get(selected.skill()), owner, level, state, selected.input(), selected.token()), "unmapped newest selected context retains identity");
        contexts.remove(selected.skill());
        check(contexts.size() == 1 && contexts.containsKey(TargetedContextTermination.Skill.VEC_DEVIATION), "target does not terminate other live context");
        check(!TargetedContextTermination.matches(contexts.get(selected.skill()), owner, level, state, selected.input(), selected.token()), "duplicate selected termination inert");
        contexts.put(selected.skill(), new TargetedContextTermination.Binding(owner, level, state, 13, 93));
        check(!TargetedContextTermination.matches(contexts.get(selected.skill()), owner, level, state, selected.input(), selected.token()), "late previous end cannot abort newer same-skill context");

        for(long input:new long[]{1,Long.MAX_VALUE})for(long token:new long[]{1,Long.MAX_VALUE})
            for(long sequence:new long[]{1,4096})for(int key=1;key<=4;key++){
                var direction=new TargetedContextTermination.FlashDirection(input,token,sequence,key);
                check(direction.equals(TargetedContextTermination.parseFlashDirection(direction.wire())),"owned direction canonical roundtrip "+direction);
                check(direction.wire().length()<=64,"owned direction fits existing payload limit");
            }
        for(String bad:List.of("", "0", "00", "01", "-1", "+1", " 1", "1 ", "1.0", "1e1", "\n1", "١", "１", "9223372036854775808", "9999999999999999999", "10000000000000000000")){
            check(TargetedContextTermination.parseFlashDirection(bad+":2:3:4")==null,"bad owned direction input "+bad);
            check(TargetedContextTermination.parseFlashDirection("1:"+bad+":3:4")==null,"bad owned direction token "+bad);
            check(TargetedContextTermination.parseFlashDirection("1:2:"+bad+":4")==null,"bad owned direction sequence "+bad);
            check(TargetedContextTermination.parseFlashDirection("1:2:3:"+bad)==null,"bad owned direction key "+bad);
        }
        for(String bad:List.of("", "1", "1:2", "1:2:3", "1:2:3:4:5", ":2:3:4", "1::3:4", "1:2::4", "1:2:3:", "1:2:4097:4", "1:2:3:5", "1:2:3:40", "1:2:3:4"+"x".repeat(65)))
            check(TargetedContextTermination.parseFlashDirection(bad)==null,"owned direction malformed/bounds "+bad);
        check(TargetedContextTermination.parseFlashDirection(null)==null,"owned direction null");
        for(long sequence:new long[]{0,-1,4097,Long.MAX_VALUE}){
            boolean threw=false;try{new TargetedContextTermination.FlashDirection(1,2,sequence,1);}catch(IllegalArgumentException expected){threw=true;}
            check(threw,"owned direction constructor sequence bound");
        }
        for(int key:new int[]{0,-1,5,Integer.MAX_VALUE}){
            boolean threw=false;try{new TargetedContextTermination.FlashDirection(1,2,1,key);}catch(IllegalArgumentException expected){threw=true;}
            check(threw,"owned direction constructor key bound");
        }
        var direction=TargetedContextTermination.parseFlashDirection("11:91:1:3");
        check(direction!=null&&TargetedContextTermination.matches(bound,owner,level,state,direction.input(),direction.token()),"retained unmapped Flashing direction keeps accepted identity");
        check(!TargetedContextTermination.matches(bound,new Object(),level,state,direction.input(),direction.token()),"owned direction rejects owner replacement");
        check(!TargetedContextTermination.matches(bound,owner,new Object(),state,direction.input(),direction.token()),"owned direction rejects Level replacement");
        check(!TargetedContextTermination.matches(bound,owner,level,new Object(),direction.input(),direction.token()),"owned direction rejects state replacement");
        check(!TargetedContextTermination.matches(bound,owner,level,state,direction.input(),direction.token()+1),"owned direction rejects wrong accepted token");

        for(var skill:TargetedContextTermination.Skill.values())for(long input:new long[]{1,42,Long.MAX_VALUE}){
            var pending=new TargetedContextTermination.PendingRequest(skill,input);
            check(pending.equals(TargetedContextTermination.parsePending(pending.wire())),"pending fixed identity canonical roundtrip "+pending);
            check(pending.wire().length()<=64,"pending request fits existing value bound");
            check(TargetedContextTermination.parse(pending.wire())==null,"pending two-field wire cannot weaken accepted token contract");
        }
        for(String id:List.of("vec_deviation","vec_reflection","flashing","storm_wing"))
            for(String bad:List.of("", "0", "00", "01", "-1", "+1", " 1", "1 ", "1.0", "1e1", "\n1", "١", "１", "9223372036854775808", "9999999999999999999", "10000000000000000000"))
                check(TargetedContextTermination.parsePending(id+":"+bad)==null,"pending original input rejects "+bad);
        for(String bad:List.of("", "vec_deviation", ":1", "Vec_deviation:1", "vec_deviation :1", "railgun:1", "plasma_cannon:1", "vec_deviation:1:0", "vec_deviation:1:2", "vec_deviation::1", "vec_deviation:1\n", "vec_deviation:1"+"x".repeat(65)))
            check(TargetedContextTermination.parsePending(bad)==null,"pending malformed/whitelist wire "+bad);
        check(TargetedContextTermination.parsePending(null)==null,"pending null wire");
        for(long input:new long[]{0,-1,Long.MIN_VALUE}){
            boolean threw=false;try{new TargetedContextTermination.PendingRequest(TargetedContextTermination.Skill.VEC_DEVIATION,input);}catch(IllegalArgumentException expected){threw=true;}
            check(threw,"pending request constructor has no nonce wildcard");
        }
        Map<TargetedContextTermination.Skill,TargetedContextTermination.Binding> pendingContexts=new LinkedHashMap<>();
        pendingContexts.put(TargetedContextTermination.Skill.VEC_DEVIATION,new TargetedContextTermination.Binding(owner,level,state,1,101));
        pendingContexts.put(TargetedContextTermination.Skill.VEC_REFLECTION,new TargetedContextTermination.Binding(owner,level,state,1,102));
        var pending=TargetedContextTermination.parsePending("vec_deviation:1");
        var pendingHold=pendingContexts.get(pending.skill());
        check(TargetedContextTermination.matches(pendingHold,owner,level,state,pending.input(),pendingHold.token()),"server-derived token selects exact old skill despite coincident input");
        check(!TargetedContextTermination.matches(pendingHold,new Object(),level,state,pending.input(),pendingHold.token()),"server-derived pending token cannot bypass owner replacement");
        pendingContexts.remove(pending.skill());
        check(pendingContexts.size()==1&&pendingContexts.containsKey(TargetedContextTermination.Skill.VEC_REFLECTION),"newly mapped reflection with same input survives deviation pending cancel");
        check(pendingContexts.get(pending.skill())==null,"duplicate original pending cancel has no newer-skill fallback");
        System.out.println("PASS TargetedContextTerminationTest " + checks + " checks");
    }
}
