package cn.academy.port.client;

/** Production local fence tests: a server proof must still separately authenticate real payment. */
public final class WindPressureLiftReceiptRegressionTest {
    private static int checks;
    private static void check(boolean c,String label) {checks++;if(!c) throw new AssertionError(label);}
    public static void main(String[] args) {
        delayedSnapshotNotificationChecks();
        var s=new WindPressureLiftReceiptState();
        check(!s.begin(0,1),"invalid input");check(s.begin(1,9),"fresh capture");
        check(!s.receive(2,9,100,1,false),"foreign input cannot occupy grant");
        check(!s.receive(1,8,100,1,false),"foreign epoch cannot occupy grant");
        check(s.receive(1,9,100,1,false),"matching grant");
        check(!s.receive(1,9,101,2,false),"changed token denied");
        check(s.available(1,9,100,1,20),"one fresh candidate");
        s.applied(1,20);
        check(!s.available(1,9,100,1,21),"one sequence cannot apply twice");
        check(s.receive(1,9,100,2,false),"new server proof");
        check(!s.available(1,9,100,2,20),"two server grants cannot double apply one client tick");
        check(s.available(1,9,100,2,21),"next tick can consume new proof");
        s.retire(2);check(!s.available(1,9,100,2,22),"refused proof cannot be banked");
        check(!s.receive(1,9,100,1,false),"reordered old packet refused");
        check(s.receive(1,9,100,2,true),"same sequence terminal closes owner");
        check(!s.receive(1,9,100,3,false),"late grant cannot reopen terminal owner");
        check(!s.begin(1,9),"same input cannot reopen");check(s.begin(2,9),"new input allowed");
        s.cancel(3,9);check(!s.receive(2,9,101,1,false),"off edge closes original owner before packet");
        check(!s.begin(3,9),"cancellation floor retained");check(s.begin(4,9),"later physical input allowed");
        s.clear();check(!s.receive(4,9,102,1,false),"pause/menu/reload clear fences queued grant");
        check(!s.begin(4,9),"clear retains replay fence");
        s.resetConnection();check(s.begin(1,1),"new connection independent");
        check(!s.available(1,1,1,1,1),"capture alone is no permission");
        latestLookupChecks();
        snapshotTravelChecks();
        System.out.println("PASS WindPressureLiftReceiptRegressionTest: "+checks+" checks");
    }
    /** A late packet notification cannot revoke a snapshot already used by two distinct native travels. */
    private static void delayedSnapshotNotificationChecks() {
        var s=new WindPressureLiftReceiptState();
        var fixed=new WindPressureLiftReceiptState.Geometry(142.5,220,131,0,0,0,1);
        var changed=new WindPressureLiftReceiptState.Geometry(142.51,220,131,0,0,0,1);
        check(s.begin(1,1),"late notification witness captures exact input");
        check(s.observeSnapshot(1,1,1,3),"shared seq3 snapshot binds token before queued delivery");
        check(s.bindGeometry(1,fixed),"shared snapshot freezes cast geometry");
        check(s.retireTravel(1,1,1,3,592)&&s.ownsTravel(1,1,1,3,592),"first distinct snapshot travel owns its claim");
        check(s.observeSnapshot(1,1,1,3)&&s.retireTravel(1,1,1,3,593),"second distinct travel observes same paid snapshot");
        check(s.ownsTravel(1,1,1,3,593)&&!s.ownsTravel(1,1,1,3,592),"second travel supersedes only prior travel ownership");
        check(!s.receive(1,1,1,3,false),"delayed same-sequence packet is rejected as duplicate");
        check(s.matches(1,1),"late duplicate must leave local capture open");
        check(s.expectedToken()==1,"late duplicate preserves bound token");
        check(s.geometryMatches(fixed)&&!s.geometryMatches(changed),"late duplicate preserves exact frozen geometry");
        check(s.ownsTravel(1,1,1,3,593),"late duplicate preserves current retired travel ownership");
        check(!s.ownsTravel(1,1,1,3,592)&&!s.retireTravel(1,1,1,3,593),"late duplicate cannot restore or replenish retired travel");
        check(s.observeSnapshot(1,1,1,3)&&s.retireTravel(1,1,1,3,594),
                "local fence permits next distinct travel; independent server clock and quota still decide consumption");
        check(s.terminal(1,1,1)&&!s.matches(1,1),"matching server END still closes snapshot session");
        check(!s.ownsTravel(1,1,1,3,594)&&!s.observeSnapshot(1,1,1,3),"END prevents delayed snapshot resurrection");
    }
    private static void snapshotTravelChecks() {
        var s=new WindPressureLiftReceiptState();
        check(s.begin(20,2),"new dual-clock capture");
        check(s.observeSnapshot(20,2,800,1),"current snapshot locks token");
        check(!s.ownsTravel(20,2,800,1,101),"snapshot alone cannot commit a travel");
        check(s.retireTravel(20,2,800,1,101),"first genuine travel retired before callbacks");
        check(s.ownsTravel(20,2,800,1,101),"retired real travel owns current snapshot");
        check(!s.retireTravel(20,2,800,1,101),"same real travel never consumed twice");
        check(s.observeSnapshot(20,2,800,1),"same paid snapshot can be observed again");
        check(s.retireTravel(20,2,800,1,102),"second true travel uses snapshot subject to independent server quota");
        check(!s.ownsTravel(20,2,800,1,101),"prior travel cannot commit after next one");
        check(!s.observeSnapshot(20,2,801,2),"snapshot observation cannot swap token");
        check(s.observeSnapshot(20,2,800,2),"Post version refresh observed");
        check(!s.ownsTravel(20,2,800,1,102),"refresh inside safety callback invalidates prior version");
        check(!s.retireTravel(20,2,800,2,102),"new version does not replenish an already retired travel");
        check(!s.observeSnapshot(20,2,800,1),"old queued version cannot replace current snapshot");
        check(s.retireTravel(20,2,800,2,104),"missed true step 103 is not banked");
        check(!s.retireTravel(20,2,800,2,103),"old real step cannot replay later");
        s.cancel(20,2);check(!s.ownsTravel(20,2,800,2,104),"cancel callback closes final travel claim");
        check(!s.observeSnapshot(20,2,800,3),"late snapshot cannot revive cancelled cast");
        check(s.begin(21,2)&&s.expectedToken()==0,"fresh press owns fresh token bootstrap");
        check(s.observeSnapshot(21,2,801,1)&&s.retireTravel(21,2,801,1,105),"new session still uses current native serial only");
        check(!s.terminal(20,2,800)&&s.ownsTravel(21,2,801,1,105),"old END cannot revoke another session");
        check(s.terminal(21,2,801)&&!s.ownsTravel(21,2,801,1,105),"current END wins before setter");
    }
    private static void latestLookupChecks() {
        var s=new WindPressureLiftReceiptState();
        var fixed=new WindPressureLiftReceiptState.Geometry(12,64,-7,0,0,0,1);
        check(s.begin(11,4),"capture for direct lookup");check(s.expectedToken()==0,"only fresh capture bootstraps token zero");
        check(s.observeCurrent(11,4,100,7),"current proof available despite seven missing queued packets");
        check(s.expectedToken()==100,"first exact proof locks token");check(s.bindGeometry(100,fixed),"first proof freezes geometry");
        check(s.observeCurrent(11,4,100,7) && s.available(11,4,100,7,80),"same observed unconsumed proof stays available");
        check(!s.receive(11,4,100,6,false),"late older packet cannot replace direct newer proof");
        check(!s.receive(11,4,100,7,false) && s.available(11,4,100,7,80),"same queued notification cannot clear unconsumed proof");
        check(!s.observeCurrent(11,4,101,8) && s.expectedToken()==100,"token cannot rebootstrap within capture");
        s.applied(7,80);
        check(!s.observeCurrent(11,4,100,7) && !s.available(11,4,100,7,81),"consumed proof cannot be reused by another native input");
        check(s.geometryMatches(fixed),"consuming per-tick permission preserves frozen cast geometry");
        check(!s.geometryMatches(new WindPressureLiftReceiptState.Geometry(12.01,64,-7,0,0,0,1)),"anchor drift refused across proof retirement");
        check(!s.geometryMatches(new WindPressureLiftReceiptState.Geometry(12,64,-7,1,0,0,1)),"mode change refused across proof retirement");
        check(!s.geometryMatches(new WindPressureLiftReceiptState.Geometry(12,64,-7,0,1,0,0)),"direction change refused across proof retirement");
        check(!s.bindGeometry(101,fixed),"foreign token cannot replace frozen geometry");
        check(s.observeCurrent(11,4,100,9),"packet gap skips historical sequence eight");
        check(!s.available(11,4,100,9,80) && s.available(11,4,100,9,81),"at most one addition per native client tick");
        check(!s.receive(11,4,100,8,false) && s.available(11,4,100,9,81),"late intermediate packet preserves newer current proof");
        check(!s.terminal(11,4,101),"foreign terminal token cannot close owned capture");
        check(s.terminal(11,4,100),"matching END closes regardless of older network sequence");
        check(!s.observeCurrent(11,4,100,10),"END blocks later direct lookup from resurrecting capture");
        check(!s.receive(11,4,100,10,false),"END blocks queued active packet");
        check(s.terminal(11,4,100),"matching END can acknowledge after local closure");
        check(!s.begin(11,4),"ended input cannot restart");check(s.begin(12,4),"fresh input opens new capture");
        check(s.expectedToken()==0,"new capture clears only its own token binding");
        check(s.observeCurrent(12,4,101,1) && s.bindGeometry(101,new WindPressureLiftReceiptState.Geometry(20,70,0,1,1,0,0)),"new input may choose new geometry");
        s.cancel(12,4);
        check(!s.observeCurrent(12,4,101,2),"menu or free-off cancellation wins before lookup");
        check(s.terminal(12,4,101),"cancelled free-off still accepts server END");
        Object world=new Object(),player=new Object(),connection=new Object();
        var captured=new WindPressureLiftReceiptState.Delivery(world,player,connection,8);
        check(WindPressureLiftReceiptState.sameDelivery(captured,new WindPressureLiftReceiptState.Delivery(world,player,connection,8)),"same queued live delivery allowed");
        check(!WindPressureLiftReceiptState.sameDelivery(captured,new WindPressureLiftReceiptState.Delivery(world,player,connection,9)),"reload generation rejects old queued delivery");
        check(!WindPressureLiftReceiptState.sameDelivery(captured,new WindPressureLiftReceiptState.Delivery(new Object(),player,connection,8)),"world replacement rejects old queued delivery");
        check(!WindPressureLiftReceiptState.sameDelivery(captured,new WindPressureLiftReceiptState.Delivery(world,new Object(),connection,8)),"player replacement rejects old queued delivery");
        check(!WindPressureLiftReceiptState.sameDelivery(captured,new WindPressureLiftReceiptState.Delivery(world,player,new Object(),8)),"connection replacement rejects old queued delivery");
        check(!WindPressureLiftReceiptState.sameDelivery(new WindPressureLiftReceiptState.Delivery(null,null,null,8),new WindPressureLiftReceiptState.Delivery(null,null,null,8)),"null delivery cannot authenticate");
        s.resetConnection();check(s.begin(11,4),"new connection may reuse numeric input and epoch");
        var replacement=new WindPressureLiftReceiptState.Delivery(new Object(),new Object(),new Object(),9);
        if(WindPressureLiftReceiptState.sameDelivery(captured,replacement))s.receive(11,4,100,99,false);
        check(s.expectedToken()==0 && !s.available(11,4,100,99,1),"old queued generation cannot repopulate reset connection");
        check(s.observeCurrent(11,4,200,1),"new connection exact current proof can bootstrap");
        s.clear();check(!s.observeCurrent(11,4,200,2),"clear wins against direct current lookup");
        var commit=new WindPressureLiftReceiptState();check(commit.begin(1,1),"callback cancellation capture");
        check(commit.observeCurrent(1,1,300,1) && commit.bindGeometry(300,fixed),"callback candidate exact owner");
        check(!commit.ownsRetired(1,1,300,1),"unretired proof cannot bypass one-shot proposal fence");
        commit.retire(1);check(commit.ownsRetired(1,1,300,1),"retired but open proposal may atomically consume");
        check(commit.observeCurrent(1,1,300,2),"callback can observe newer current publication");
        check(!commit.ownsRetired(1,1,300,1),"newer callback publication invalidates old proposal commit");
        commit.retire(2);check(commit.ownsRetired(1,1,300,2),"current retired publication remains owned");
        commit.cancel(1,1);check(!commit.ownsRetired(1,1,300,2),"abortLocal during safety query cancels commit even without input or body changes");
    }
}
