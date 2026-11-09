/* AcademyCraft1.0.7 client callback/lifecycle binding adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import java.util.*;

/** Actual input seam shared by the staged AcademyClient and the engine-free tests. */
public final class ClassicClientInputBinding {
    public static final String FLASH_GROUP="TP_Flashing",STORM_GROUP="vm_storm_wing";
    public enum Kind { SINGLE_KEY,RAILGUN,LOCATION_GUI,CONTEXT_TOGGLE,FLASH_TOGGLE,DIRECTION }
    public enum Edge { DOWN,UP,ABORT }
    // magnetic_item_pull deliberately remains SINGLE_KEY: held selection commits on physical UP and cancels on ABORT.
    private static final Set<String> INSTANT=Set.of("magnetic_ground_step","conductive_circuit","grounding_return","polarity_tether","forked_arc","arc_gen","thunder_bolt","mine_detect","electron_bomb","ray_barrage");
    private static final Set<String> CONTEXTS=Set.of("vec_deviation","vec_reflection","storm_wing","flashing");
    public static Kind kind(String skill){return skill.equals("railgun")?Kind.RAILGUN:skill.equals("location_teleport")?Kind.LOCATION_GUI:skill.equals("flashing")?Kind.FLASH_TOGGLE:CONTEXTS.contains(skill)?Kind.CONTEXT_TOGGLE:Kind.SINGLE_KEY;}

    public interface Host {
        boolean available();boolean inGame();boolean categoryActivated();boolean canUseAbility();boolean terminalOpen();
        default boolean endTickAllowed(){return available()&&inGame();}
        default int actorId(){return -1;}
        default long ownerEpoch(){return 0;}
        boolean physicalDown(int sourceKey);boolean registeredMapping(String skill);boolean eligible(Node node);
        String mappedSkill(int slot);int mappedKey(int slot);int slotCount();
        void rawActivation(boolean desired);void replaceOverrides(int[] keys);
        /** Returns the authenticated input allocated before the single start request. */
        long down(Node node);void up(Node node,long input);void abort(Node node,long input);
        /** Explicit modern positive-input pending-toggle policy; no accepted-context/token0 termination. */
        boolean cancelPending(Node node,long input);
        /** No listener exists for any of the 29 source SingleKey MSG_KEYTICK emissions. */
        default void localKeyTick(Node node){}
        default boolean railgunCharges(Node node){return false;}
        default void railgunTick(Node node,int remaining){}
        default void railgunEnd(Node node,boolean abort){}
        void direction(ContextIdentity context,int direction,Edge edge,boolean silent);
        void localContextEnd(ContextIdentity context);
        void contextAbort(TargetedContextTermination.Request request);
        /** Explicit M30 policy; must perform local cosmetic cleanup with no requests. */
        void modernCleanup(Collection<Node> defaults);
        /** Rejected prediction closes only its captured input after the final effect tick; no wire request. */
        default void rejectedSingle(Node node,long input){}
        default void rejectedSingle(Node node,long input,long capturedOwnerEpoch){rejectedSingle(node,input);}
    }

    public final class Node implements ClassicClientRuntimeCoordinator.Delegate {
        public final int slot,key,direction;public final String skill,group;public final Kind kind;
        public final ContextIdentity context;
        private long input,singleEpoch,ownerEpoch;private int actorId=-1;private boolean singleReference,singleTerminated;private int railgunTicks=-1;
        private Node(int slot,int key,String skill){this.slot=slot;this.key=key;this.skill=skill;this.kind=kind(skill);this.group=ClassicClientRuntimeCoordinator.DEFAULT_GROUP;this.direction=0;this.context=null;}
        private Node(ContextIdentity context,int key,int direction,String group){this.slot=context.node.slot;this.key=key;this.skill=context.skill;this.kind=Kind.DIRECTION;this.group=group;this.direction=direction;this.context=context;this.input=context.input;}
        public long input(){return input;}public int actorId(){return actorId;}public long ownerEpoch(){return ownerEpoch;}public long acceptedToken(){var owned=singleContexts.get(skill);return owned!=null&&owned.node==this&&owned.input==input&&owned.epoch==singleEpoch?owned.token:0;}public int railgunTicks(){return railgunTicks;}
        public boolean singleReference(){return singleReference;}
        @Override public void onKeyDown(){
            if(!usable(this))return;
            if(kind==Kind.DIRECTION){direction(this,Edge.DOWN);return;}
            if(kind==Kind.CONTEXT_TOGGLE||kind==Kind.FLASH_TOGGLE){
                var owned=contexts.get(skill);
                if(owned!=null&&!owned.terminated)requestDisposal(owned);
                else {var waiting=pending.get(skill);if(waiting!=null&&waiting.input>0){long original=waiting.input;if(host.cancelPending(waiting,original)){cancelPendingIdentity(skill,original);pending.remove(skill);}}
                    else {input=host.down(this);if(input>0)pending.put(skill,this);}}
                if(kind==Kind.FLASH_TOGGLE)runtime.requestFlush();return;
            }
            actorId=host.actorId();ownerEpoch=host.ownerEpoch();input=host.down(this);
            if(kind==Kind.SINGLE_KEY){singleReference=true;singleTerminated=false;singleEpoch++;var prior=pendingSingles.get(skill);if(prior!=null&&prior.input==0)legacyAmbiguous.add(skill);pendingSingles.put(skill,new SinglePending(this,input,singleEpoch,ownerEpoch));}
            if(kind==Kind.RAILGUN)railgunTicks=host.railgunCharges(this)?20:-1;
        }
        @Override public void onKeyTick(){
            if(discarded||silent)return;
            if(kind==Kind.SINGLE_KEY&&checkSingle())host.localKeyTick(this);
            else if(kind==Kind.RAILGUN&&railgunTicks!=-1)host.railgunTick(this,--railgunTicks);
        }
        @Override public void onKeyUp(){finish(false);}
        @Override public void onKeyAbort(){finish(true);}
        private void finish(boolean abort){
            if(discarded)return;
            if(kind==Kind.DIRECTION){direction(this,abort?Edge.ABORT:Edge.UP);return;}
            if(kind==Kind.RAILGUN){railgunTicks=-1;if(!silent&&host.available()&&host.inGame())host.railgunEnd(this,abort);return;}
            if(kind!=Kind.SINGLE_KEY||!checkSingle())return;
            var pending=pendingSingles.get(skill);if(pending!=null&&pending.node==this&&pending.epoch==singleEpoch&&pending.input==0){legacyAmbiguous.add(skill);pendingSingles.remove(skill);}
            // SK always emits a local key-message and nulls its reference. Empty listeners stay empty.
            if(!silent&&host.available()&&host.inGame()&&!INSTANT.contains(skill)&&!(abort&&skill.equals("blood_retro"))){if(abort)host.abort(this,input);else host.up(this,input);}
            singleReference=false;
        }
        private boolean checkSingle(){if(singleTerminated)singleReference=false;return singleReference;}
    }

    public static final class ContextIdentity {
        public final String skill;public final long input,token;public final Node node;
        private ClassicClientRuntimeCoordinator.ActivateHandler handler;
        private boolean disposalRequested,serverEnded,terminated,directions;private int applying=-1;
        private ContextIdentity(Node node,long token){this.node=node;this.skill=node.skill;this.input=node.input;this.token=token;}
        public boolean disposalRequested(){return disposalRequested;}public boolean terminated(){return terminated;}
        public int applying(){return applying;}public boolean directions(){return directions;}
    }
    private record Identity(String skill,long input,long token){}
    private record PendingIdentity(String skill,long input){}
    private record SinglePending(Node node,long input,long epoch,long ownerEpoch){}
    private record SingleDisposal(SinglePending pending,boolean rejected){}
    private record SingleToken(String skill,long token,long ownerEpoch){}
    private record RetiredSingle(String skill,long input,long ownerEpoch){}
    private record SkillEpoch(String skill,long ownerEpoch){}
    private static final class SingleIdentity {
        final Node node;final long input,token,epoch,ownerEpoch;boolean endRequested;
        SingleIdentity(SinglePending pending,long token){node=pending.node;input=pending.input;epoch=pending.epoch;ownerEpoch=pending.ownerEpoch;this.token=token;}
    }
    private final Host host;private final ClassicClientRuntimeCoordinator runtime;
    private final Map<String,Node> pending=new HashMap<>();private final Map<String,ContextIdentity> contexts=new LinkedHashMap<>();
    private final LinkedHashSet<Identity> tombstones=new LinkedHashSet<>();private final Set<Integer> fences=new HashSet<>();
    private final LinkedHashSet<PendingIdentity> cancelledPending=new LinkedHashSet<>();
    private final Map<String,SinglePending> pendingSingles=new HashMap<>();private final Map<String,SingleIdentity> singleContexts=new LinkedHashMap<>();
    private final Set<String> legacyAmbiguous=new HashSet<>();
    private final LinkedHashSet<RetiredSingle> retiredSingles=new LinkedHashSet<>();
    private final List<SingleDisposal> pendingSingleDisposals=new ArrayList<>();
    private final LinkedHashMap<SingleToken,Long> singleTokenInputs=new LinkedHashMap<>();
    private final Map<SkillEpoch,Long> singleHighwater=new HashMap<>();
    private boolean silent,discarded;
    public ClassicClientInputBinding(Host host){
        this.host=Objects.requireNonNull(host);
        runtime=new ClassicClientRuntimeCoordinator(new ClassicClientRuntimeCoordinator.Inputs(){
            public boolean runtimeAvailable(){return !discarded&&host.available();}public boolean playerInGame(){return host.inGame();}
            public boolean categoryActivated(){return host.categoryActivated();}public boolean canUseAbility(){return host.canUseAbility();}
            public boolean terminalOpen(){return host.terminalOpen();}
            public boolean keyDown(int key){boolean down=host.physicalDown(key);if(!down)fences.remove(key);return down;}
            public boolean inCooldown(ClassicClientRuntimeCoordinator.Delegate delegate){return !usable((Node)delegate);}
            public void requestRawActivation(boolean state){if(!discarded&&host.available())host.rawActivation(state);}
            public void replaceOverrides(int[] keys){host.replaceOverrides(keys);}
        });
        runtime.setDefaultGroupRebuilder(this::rebuildDefaults);
    }
    private boolean usable(Node node){return !discarded&&host.available()&&!fences.contains(node.key)&&host.eligible(node)&&(node.context==null||contexts.get(node.skill)==node.context&&!node.context.terminated);}
    private void rebuildDefaults(){for(int slot=0;slot<host.slotCount();slot++){String skill=host.mappedSkill(slot);int key=host.mappedKey(slot);if(key!=0&&host.registeredMapping(skill))runtime.addKey(key,new Node(slot,key,skill));}}
    public ClassicClientRuntimeCoordinator runtime(){return runtime;}
    public void tickStart(){runtime.clientTickStart();}
    public void activateKeyDown(){if(!discarded&&host.available())runtime.activateKeyDown();}
    public void observerActivate(){if(!discarded)runtime.observerActivate();}
    public void observerDeactivate(){if(!discarded)runtime.observerDeactivate();}
    public void presetSwitch(){if(!discarded)runtime.presetSwitch();}
    public void presetEdit(){if(!discarded)runtime.presetEdit();}
    public void requestFlush(){if(!discarded)runtime.requestFlush();}
    public boolean ownsDefault(int slot){return runtime.keyRegistrationSnapshot().values().stream().anyMatch(d->d instanceof Node n&&n.kind!=Kind.DIRECTION&&n.slot==slot);}
    public boolean activeDefault(int slot){for(var e:runtime.keyRegistrationSnapshot().entrySet())if(e.getValue() instanceof Node n&&n.kind!=Kind.DIRECTION&&n.slot==slot)return runtime.keyStateSnapshot().getOrDefault(e.getKey(),new ClassicClientRuntimeCoordinator.KeyStateSnapshot(false,false)).state();return false;}
    public int railgunTicks(int slot){for(var node:winningNodes())if(node.slot==slot&&node.kind==Kind.RAILGUN)return node.railgunTicks;return -1;}
    /** Source informThrowCoin directly resets the first default Railgun node, retaining runtime history. */
    public void railgunCoinAccepted(){if(discarded||!host.available()||!host.inGame())return;for(var node:defaultNodes())if(node.kind==Kind.RAILGUN){node.onKeyAbort();return;}}
    public List<Node> winningNodes(){return runtime.keyRegistrationSnapshot().values().stream().map(d->(Node)d).toList();}
    public List<Node> defaultNodes(){return runtime.getDelegates("def").stream().map(d->(Node)d).toList();}
    public ContextIdentity context(String skill){return contexts.get(skill);}
    public boolean ownsContext(String skill,long input,long token){var context=contexts.get(skill);return context!=null&&context.input==input&&context.token==token&&!context.terminated;}

    /** Called only from the effect's accepted local made-alive branch, never a raw packet switch. */
    public boolean acceptedStart(String skill,long input,long token,boolean active){
        if(discarded||!host.available()||!CONTEXTS.contains(skill)||input<=0||token<=0||contextStartCancelled(skill,input,token))return false;
        var old=contexts.get(skill);if(old!=null&&old.input==input&&old.token==token)return false;
        Node node=pending.get(skill);if(node==null||node.input!=input)return false;
        if(old!=null)end(old,false);
        var owned=new ContextIdentity(node,token);contexts.put(skill,owned);pending.remove(skill);
        // Storm source handler is unconditional while registered. Other handlers use accepted ALIVE identity.
        owned.handler=new ClassicClientRuntimeCoordinator.ActivateHandler(){
            public boolean handles(){return skill.equals("storm_wing")||!owned.terminated&&contexts.get(skill)==owned;}
            public void onKeyDown(){requestDisposal(owned);}public String getHint(){return ENDSPECIAL;}
        };
        runtime.addActivateHandler(owned.handler);
        if(skill.equals("flashing"))addDirections(owned);
        else if(skill.equals("storm_wing")&&active)addDirections(owned);
        return true;
    }
    public boolean acceptedActive(String skill,long input,long token){var owned=contexts.get(skill);if(owned==null||!ownsContext(skill,input,token))return false;if(skill.equals("storm_wing")&&!owned.directions){addDirections(owned);return true;}return false;}
    private void addDirections(ContextIdentity owned){
        owned.directions=true;boolean flash=owned.skill.equals("flashing");
        int[] keys=flash?new int[]{ClassicPhysicalKeys.A,ClassicPhysicalKeys.D,ClassicPhysicalKeys.W,ClassicPhysicalKeys.S}:new int[]{ClassicPhysicalKeys.W,ClassicPhysicalKeys.S,ClassicPhysicalKeys.A,ClassicPhysicalKeys.D};
        String group=flash?FLASH_GROUP:STORM_GROUP;for(int i=0;i<4;i++)runtime.addKey(group,keys[i],new Node(owned,keys[i],i+1,group));
    }
    private void direction(Node node,Edge edge){
        ContextIdentity owned=node.context;if(contexts.get(node.skill)!=owned)return;
        // Storm routes abort through sendToSelf; source rejects it once this context is TERMINATED.
        if(owned.terminated&&owned.skill.equals("storm_wing")){owned.applying=-1;return;}
        if(edge==Edge.DOWN){owned.applying=node.direction;host.direction(owned,node.direction,edge,silent||!host.inGame());}
        else if(owned.applying==node.direction){host.direction(owned,node.direction,edge,silent||!host.inGame());owned.applying=-1;}
    }
    private void requestDisposal(ContextIdentity context){if(!discarded&&host.available()&&host.inGame()&&contexts.get(context.skill)==context&&!context.terminated)context.disposalRequested=true;}
    /** LOWEST Post boundary: final effect MSG_TICK has run, then local termination, then one wire end. */
    public void finishLocalDisposals(){if(discarded||!host.available()||!host.inGame()||!host.endTickAllowed())return;for(var owned:new ArrayList<>(contexts.values()))if(owned.disposalRequested)end(owned,!owned.serverEnded);var iter=singleContexts.values().iterator();while(iter.hasNext()){var owned=iter.next();if(owned.endRequested){if(owned.node.singleEpoch==owned.epoch)owned.node.singleTerminated=true;iter.remove();}}for(var disposal:pendingSingleDisposals){var pending=disposal.pending;if(pending.node.singleEpoch==pending.epoch)pending.node.singleTerminated=true;if(disposal.rejected)host.rejectedSingle(pending.node,pending.input,pending.ownerEpoch);}pendingSingleDisposals.clear();}
    /** Matching server termination is idempotent after a deferred local termination. */
    public boolean acceptedEnd(String skill,long input,long token){var owned=contexts.get(skill);if(owned==null||owned.input!=input||owned.token!=token)return false;owned.disposalRequested=true;owned.serverEnded=true;return true;}
    private void end(ContextIdentity owned,boolean send){
        if(contexts.get(owned.skill)!=owned||owned.terminated)return;owned.terminated=true;tombstone(new Identity(owned.skill,owned.input,owned.token));
        if(owned.skill.equals("flashing")){runtime.removeActiveHandler(owned.handler);runtime.clearKeys(FLASH_GROUP);}
        else if(owned.skill.equals("storm_wing")){runtime.clearKeys(STORM_GROUP);runtime.removeActiveHandler(owned.handler);}
        else runtime.removeActiveHandler(owned.handler);
        contexts.remove(owned.skill);host.localContextEnd(owned);
        if(send&&!silent&&host.available()&&host.inGame())host.contextAbort(new TargetedContextTermination.Request(TargetedContextTermination.Skill.find(owned.skill),owned.input,owned.token));
    }
    private void tombstone(Identity identity){tombstones.add(identity);while(tombstones.size()>256)tombstones.remove(tombstones.iterator().next());}
    private void cancelPendingIdentity(String skill,long input){cancelledPending.add(new PendingIdentity(skill,input));while(cancelledPending.size()>256)cancelledPending.remove(cancelledPending.iterator().next());}
    /** Additional modern cancellation fence; does not replace source per-key history. */
    public boolean contextStartCancelled(String skill,long input,long token){return discarded||tombstones.contains(new Identity(skill,input,token))||cancelledPending.contains(new PendingIdentity(skill,input));}
    /** Legacy input0 binds only at an accepted local start to the pending owned node; token remains exact. */
    public boolean acceptedSingleStart(String skill,long input,long token){if(!singleStartAllowed(skill,input,token))return false;var pending=pendingSingles.get(skill);singleContexts.put(skill,new SingleIdentity(pending,token));pendingSingles.remove(skill);rememberSingleToken(skill,input,token,pending.ownerEpoch);return true;}
    /** The accepted exact endpoint marks disposed; checkContext sees TERMINATED after the END boundary. */
    public boolean acceptedSingleEnd(String skill,long input,long token){if(discarded)return false;var owned=singleContexts.get(skill);if(owned!=null&&owned.input==input&&owned.token==token){owned.endRequested=true;if(input>0)retireSingle(skill,input,owned.ownerEpoch);rememberSingleToken(skill,input,token,owned.ownerEpoch);return true;}if(!singleTerminalAllowed(skill,input,token))return false;var pending=pendingSingles.remove(skill);retireSingle(skill,input,pending.ownerEpoch);rememberSingleToken(skill,input,token,pending.ownerEpoch);pendingSingleDisposals.add(new SingleDisposal(pending,false));return true;}
    /** Nonmutating packet admission, before an old start can replace a new local cosmetic context. */
    public boolean singleStartAllowed(String skill,long input,long token){if(discarded||!host.available()||input<0||token<=0||input==0&&legacyAmbiguous.contains(skill)||input>0&&singleInputRetired(skill,input)||singleTokenConflicts(skill,input,token))return false;if(input>0&&cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)!=null&&token<=singleHighwater.getOrDefault(new SkillEpoch(skill,host.ownerEpoch()),0L))return false;var pending=pendingSingles.get(skill);if(pending==null||pending.input!=input||pending.node.singleEpoch!=pending.epoch||input>0&&cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)!=null&&pending.ownerEpoch!=host.ownerEpoch())return false;var old=singleContexts.get(skill);return old==null||old.token<token;}
    /** A positive exact pending terminal is meaningful even when no loop start was ever accepted. */
    public boolean singleTerminalAllowed(String skill,long input,long token){if(discarded||!host.available()||input<0||token<=0||singleTokenConflicts(skill,input,token))return false;var owned=singleContexts.get(skill);if(owned!=null&&owned.input==input&&owned.token==token)return true;if(input<=0||cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)==null||singleInputRetired(skill,input)||token<=singleHighwater.getOrDefault(new SkillEpoch(skill,host.ownerEpoch()),0L))return false;var pending=pendingSingles.get(skill);return pending!=null&&pending.input==input&&pending.node.singleEpoch==pending.epoch&&pending.ownerEpoch==host.ownerEpoch();}
    public boolean rejectedSingle(String skill,long input){if(discarded||input<=0||cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)==null||singleInputRetired(skill,input))return false;var pending=pendingSingles.get(skill);if(pending==null||pending.input!=input||pending.node.singleEpoch!=pending.epoch)return false;pendingSingles.remove(skill);retireSingle(skill,input,pending.ownerEpoch);pendingSingleDisposals.add(new SingleDisposal(pending,true));return true;}
    /** Explicit modern ownership metadata barrier; source activation/preset observers stay independent. */
    public void retireSingleOwnerEpoch(){
        var pendingIter=pendingSingles.entrySet().iterator();while(pendingIter.hasNext()){var entry=pendingIter.next();if(cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(entry.getKey())!=null){var value=entry.getValue();retireSingle(entry.getKey(),value.input,value.ownerEpoch);pendingSingleDisposals.add(new SingleDisposal(value,true));pendingIter.remove();}}
        for(var owned:singleContexts.values())if(cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(owned.node.skill)!=null){owned.endRequested=true;retireSingle(owned.node.skill,owned.input,owned.ownerEpoch);pendingSingleDisposals.add(new SingleDisposal(new SinglePending(owned.node,owned.input,owned.epoch,owned.ownerEpoch),true));}
        singleTokenInputs.clear();singleHighwater.clear();
    }
    public boolean singleInputRetired(String skill,long input){return discarded||input>0&&retiredSingles.contains(new RetiredSingle(skill,input,host.ownerEpoch()));}
    public boolean hasPositiveSingleOwnership(String skill){var pending=pendingSingles.get(skill);if(pending!=null&&pending.input>0)return true;var owned=singleContexts.get(skill);return owned!=null&&owned.input>0&&!owned.endRequested;}
    public boolean singleTokenConflicts(String skill,long input,long token){Long known=singleTokenInputs.get(new SingleToken(skill,token,host.ownerEpoch()));return known!=null&&known!=input;}
    private void rememberSingleToken(String skill,long input,long token,long ownerEpoch){if(input<=0||token<=0||cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)==null)return;singleTokenInputs.put(new SingleToken(skill,token,ownerEpoch),input);singleHighwater.merge(new SkillEpoch(skill,ownerEpoch),token,Math::max);while(singleTokenInputs.size()>256)singleTokenInputs.remove(singleTokenInputs.keySet().iterator().next());}
    private void retireSingle(String skill,long input,long ownerEpoch){if(input<=0)return;retiredSingles.add(new RetiredSingle(skill,input,ownerEpoch));while(retiredSingles.size()>256)retiredSingles.remove(retiredSingles.iterator().next());}

    /** M30 acknowledgement/rejection/category/preset policy, explicitly separate from source observers. */
    public void modernSnapshotFallback(){
        if(discarded)return;var old=defaultNodes();fenceRegistered();silent=true;
        try{runtime.abortDelegates();host.modernCleanup(old);var pendingIter=pending.entrySet().iterator();while(pendingIter.hasNext()){var entry=pendingIter.next();if(!host.registeredMapping(entry.getKey())){cancelPendingIdentity(entry.getKey(),entry.getValue().input);pendingIter.remove();}}for(var node:pendingSingles.values())if(node.input==0)legacyAmbiguous.add(node.node.skill);else if(cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(node.node.skill)!=null)retireSingle(node.node.skill,node.input,node.ownerEpoch);pendingSingles.clear();runtime.updateDefaultGroup();fenceRegistered();}finally{silent=false;}
    }
    public void establishSessionFence(){fenceRegistered();}
    private void fenceRegistered(){for(int key:runtime.keyRegistrationSnapshot().keySet())if(host.physicalDown(key))fences.add(key);}
    /** Death/player/level/connection replacement discards ownership without any old callback or request. */
    public void discard(){discarded=true;pending.clear();contexts.clear();pendingSingles.clear();singleContexts.clear();retiredSingles.clear();pendingSingleDisposals.clear();singleTokenInputs.clear();singleHighwater.clear();fences.clear();host.replaceOverrides(new int[0]);}
    public boolean discarded(){return discarded;}
    public boolean singleStatusUnknown(String skill){return legacyAmbiguous.contains(skill);}
    public Set<Integer> fenceSnapshot(){return Set.copyOf(fences);}
}
