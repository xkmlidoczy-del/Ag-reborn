#!/usr/bin/env python3
"""Compile and exercise the actual pure NEW311 ledger; no Minecraft/NeoForge shims."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tempfile


HARNESS = r'''
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import cn.academy.port.highpower.core.DisplacedNeedleLedger.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class DisplacedNeedleLedgerOracle {
    static final UUID OWNER = UUID.fromString("12345678-0000-4000-8000-000000000001");
    static final UUID OTHER = UUID.fromString("12345678-0000-4000-8000-000000000002");
    static final UUID ENTITY = UUID.fromString("12345678-0000-4000-8000-000000000003");
    static int assertions;
    static void yes(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    static void no(boolean value, String why) { yes(!value, why); }
    static void eq(Object expected, Object actual, String why) { yes(Objects.equals(expected, actual), why + ": " + actual); }
    static void accounted(DisplacedNeedleLedger ledger) {
        Snapshot s = ledger.snapshot();
        yes(s.removedItems() == 0 || s.removedItems() == 1, "bounded exact item debit");
        eq(s.removedItems(), s.accountedItems(), "one debit has exactly one custody outcome");
        yes(s.nonce() >= 0 && s.generation() >= 0, "counter cannot wrap");
    }
    record Fixture(DisplacedNeedleLedger ledger, Token token, Object entity, AttackPermit attack) {}
    static Fixture pending() {
        DisplacedNeedleLedger l = new DisplacedNeedleLedger(OWNER);
        Token t = l.reserve(); Object e = new Object();
        yes(l.escrow(t, 1, true), "one real plain item escrowed");
        yes(l.publishPending(t, ENTITY, e), "candidate registered inertly");
        return new Fixture(l, t, e, null);
    }
    static Fixture active() {
        Fixture f = pending();
        AttackPermit a = f.ledger.activate(f.token, ENTITY, f.entity, true, true);
        yes(a != null, "paid validated spawn activates");
        return new Fixture(f.ledger, f.token, f.entity, a);
    }

    static void reservationAndIdentity() {
        DisplacedNeedleLedger l = new DisplacedNeedleLedger(OWNER);
        Token t = l.reserve(); eq(1L, t.nonce(), "positive first nonce");
        eq(null, l.reserve(), "reservation blocks reentry before removing offhand");
        no(l.escrow(t, 0, true), "no zero-item enhancement");
        no(l.escrow(t, 2, true), "no whole-stack enhancement");
        no(l.escrow(t, 1, false), "nonplain metadata refused");
        DisplacedNeedleLedger other = new DisplacedNeedleLedger(OWNER);
        Token alien = other.reserve();
        no(l.escrow(alien, 1, true), "same-owner same-nonce token cannot cross ledger");
        yes(l.escrow(t, 1, true), "canonical exact one accepted");
        no(l.escrow(t, 1, true), "escrow debit replay refused");
        Object entity = new Object();
        yes(l.publishPending(t, ENTITY, entity), "pending registered");
        eq(null, l.activate(t, ENTITY, entity, false, true), "rejected spawn cannot attack");
        eq(null, l.activate(t, ENTITY, entity, true, false), "unpaid entity cannot attack");
        eq(null, l.activate(t, ENTITY, new Object(), true, true), "UUID clone cannot activate");
        eq(null, l.activate(t, OTHER, entity, true, true), "wrong entity UUID cannot activate");
        eq(null, l.cancel(t, ENTITY, new Object(), Disposal.CANDIDATE_DISCARDED), "wrong object cannot authorize refund");
        accounted(l);
        AttackPermit a = l.activate(t, ENTITY, entity, true, true);
        yes(a != null, "correct activation permitted");
        eq(1L, a.generation(), "first damaging generation");
        eq(null, l.activate(t, ENTITY, entity, true, true), "activation replay refused");
        eq(null, l.cancel(t, ENTITY, entity, Disposal.CANDIDATE_DISCARDED), "published item never refunded");
        eq(null, l.reserve(), "one active per owner");
        no(l.mayDamage(a, OTHER, 1, ENTITY, entity), "owner proof required");
        no(l.mayDamage(a, OWNER, 2, ENTITY, entity), "nonce proof required");
        no(l.mayDamage(a, OWNER, 1, ENTITY, new Object()), "same UUID different identity refused");
        yes(l.claimDamage(a, OWNER, 1, ENTITY, entity), "first damage claim succeeds");
        no(l.claimDamage(a, OWNER, 1, ENTITY, entity), "damage reentry/replay fails");
        accounted(l);
    }

    static void cancelledPublicationAndRefund() {
        Fixture f = pending(); DisplacedNeedleLedger l = f.ledger;
        int inventory = 0, originalEscrow = 1, inserted = 0;
        ReturnTicket r = l.cancel(f.token, ENTITY, f.entity, Disposal.CANDIDATE_DISCARDED);
        yes(r != null, "discarded inert candidate may return original escrow");
        eq(null, l.cancel(f.token, ENTITY, f.entity, Disposal.CANDIDATE_DISCARDED), "one ticket only");
        eq(null, l.reserve(), "refund escrow blocks new reservations");
        no(l.finishReturn(r, true), "cannot confirm a transfer before beginning it");
        yes(l.beginReturn(r), "move custody into retained refund escrow before callback");
        no(l.beginReturn(r), "reentrant insertion denied");
        eq(Custody.REFUND_ESCROW, l.snapshot().custody(), "callback custody explicit");
        yes(l.finishReturn(r, false), "full inventory retains original for retry");
        eq(1, originalEscrow, "full inventory loses no item");
        yes(l.beginReturn(r), "same retained escrow can retry");
        inventory++; originalEscrow--; inserted++;
        yes(l.finishReturn(r, true), "successful exact insertion finalized");
        no(l.finishReturn(r, true), "refund confirmation replay denied");
        no(l.beginReturn(r), "used ticket cannot insert again");
        eq(1, inventory + originalEscrow, "physical one-item conservation");
        eq(1, inserted, "one successful refund");
        Token next = l.reserve(); eq(2L, next.nonce(), "cancelled reservation nonce never reused");
        no(l.escrow(f.token, 1, true), "old token rejected next cycle");
        eq(null, l.cancel(next, null, null, Disposal.NO_CANDIDATE), "zero debit cancels with no grant");
        eq(State.SPENT, l.snapshot().state(), "empty reservation settles");
        accounted(l);

        Fixture lost = pending();
        eq(null, lost.ledger.cancel(lost.token, ENTITY, lost.entity, Disposal.NO_CANDIDATE), "candidate needs proven discard");
        eq(State.LOST, lost.ledger.snapshot().state(), "uncertain spawn stays outstanding");
        eq(null, lost.ledger.reserve(), "unknown old entity blocks replacement");
        eq(null, lost.ledger.cancel(lost.token, ENTITY, lost.entity, Disposal.CANDIDATE_DISCARDED), "uncertain publication cannot replay refund");
        accounted(lost.ledger);
    }

    static void reprojectionAndNativeDisposition() {
        Fixture f = active(); DisplacedNeedleLedger l = f.ledger;
        AttackPermit second = l.beginReprojection(OWNER, 1, ENTITY, f.entity);
        yes(second != null, "same live entity permits reprojection");
        eq(2L, second.generation(), "generation monotonic");
        no(l.mayDamage(f.attack, OWNER, 1, ENTITY, f.entity), "old attack retired before aim/payment");
        no(l.mayDamage(second, OWNER, 1, ENTITY, f.entity), "new permit inert until commit");
        eq(null, l.beginReprojection(OWNER, 1, ENTITY, f.entity), "reprojection reentry denied");
        no(l.completeReprojection(second, false, true), "bad aim remains inert");
        no(l.completeReprojection(second, true, true), "failed attempt cannot replay commit");
        no(l.mayDamage(f.attack, OWNER, 1, ENTITY, f.entity), "failed aim cannot revive old generation");
        AttackPermit third = l.beginReprojection(OWNER, 1, ENTITY, f.entity);
        no(l.completeReprojection(third, true, false), "unpaid reprojection remains inert");
        AttackPermit fourth = l.beginReprojection(OWNER, 1, ENTITY, f.entity);
        yes(l.abortReprojection(fourth), "cancel consumes attempt");
        no(l.completeReprojection(fourth, true, true), "aborted attempt cannot activate");
        AttackPermit fifth = l.beginReprojection(OWNER, 1, ENTITY, f.entity);
        yes(l.completeReprojection(fifth, true, true), "valid paid same-entity reprojection");
        eq(5L, fifth.generation(), "failed attempts still retire generations");
        eq(ENTITY, l.snapshot().entityId(), "entity identity never replaced");
        no(l.settleEntity(OTHER, 1, ENTITY, f.entity, Resolution.NATIVE_PICKUP), "stranger cannot settle ownership");
        no(l.settleEntity(OWNER, 1, ENTITY, new Object(), Resolution.NATIVE_PICKUP), "clone cannot settle ownership");
        l.retireAttack();
        yes(l.settleEntity(OWNER, 1, ENTITY, f.entity, Resolution.NATIVE_PICKUP), "native whole pickup settles without refund");
        no(l.settleEntity(OWNER, 1, ENTITY, f.entity, Resolution.NATIVE_PICKUP), "pickup replay refused");
        no(l.mayDamage(fifth, OWNER, 1, ENTITY, f.entity), "pickup retires damage");
        eq(null, l.cancel(f.token, ENTITY, f.entity, Disposal.CANDIDATE_DISCARDED), "native pickup grants no second item");
        accounted(l);
        eq(2L, l.reserve().nonce(), "next medium uses new nonce");
    }

    static void unloadReloadAndDeath() {
        Fixture f = active(); DisplacedNeedleLedger l = f.ledger;
        yes(l.unload(OWNER, 1, ENTITY, f.entity), "unload accepted");
        eq(State.ACTIVE, l.snapshot().state(), "unload keeps outstanding identity");
        no(l.mayDamage(f.attack, OWNER, 1, ENTITY, f.entity), "unloaded attack retired");
        eq(null, l.reserve(), "unload never permits replacement");
        eq(null, l.beginReprojection(OWNER, 1, ENTITY, f.entity), "missing object cannot reproject");
        Object reloaded = new Object();
        no(l.bindLive(OWNER, 1, ENTITY, reloaded, false), "load requires native validation");
        no(l.bindLive(OTHER, 1, ENTITY, reloaded, true), "load owner mismatch");
        no(l.bindLive(OWNER, 2, ENTITY, reloaded, true), "load nonce mismatch");
        yes(l.bindLive(OWNER, 1, ENTITY, reloaded, true), "validated same UUID can return inertly");
        no(l.bindLive(OWNER, 1, ENTITY, new Object(), true), "second clone cannot replace binding");
        no(l.mayDamage(f.attack, OWNER, 1, ENTITY, reloaded), "rebinding never revives old permit");
        AttackPermit later = l.beginReprojection(OWNER, 1, ENTITY, reloaded);
        yes(l.completeReprojection(later, true, true), "reloaded original can be explicitly reprojected");
        DisplacedNeedleLedger restored = DisplacedNeedleLedger.restore(OWNER, l.toDecimalMap());
        eq(l.snapshot(), restored.snapshot(), "active durable identity roundtrip");
        no(restored.mayDamage(later, OWNER, 1, ENTITY, reloaded), "runtime capability cannot survive restore");
        eq(null, restored.reserve(), "saved outstanding entity blocks replacement");
        yes(restored.bindLive(OWNER, 1, ENTITY, reloaded, true), "restored original may bind inertly");
        restored.ownerDied();
        eq(State.LOST, restored.snapshot().state(), "death preserves unresolved item accounting");
        eq(null, restored.reserve(), "death does not authorize replacement or refund");
        yes(restored.settleEntity(OWNER, 1, ENTITY, reloaded, Resolution.DISCARDED), "verified native discard settles dead owner record");
        accounted(restored);

        Fixture dying = active(); dying.ledger.ownerDied();
        no(dying.ledger.mayDamage(dying.attack, OWNER, 1, ENTITY, dying.entity), "death retires attack immediately");
        eq(null, dying.ledger.cancel(dying.token, ENTITY, dying.entity, Disposal.CANDIDATE_DISCARDED), "death cannot refund active item");
        Fixture interrupted = pending();
        DisplacedNeedleLedger interruptedRestore = DisplacedNeedleLedger.restore(OWNER, interrupted.ledger.toDecimalMap());
        eq(State.PENDING, interruptedRestore.snapshot().state(), "interrupted publication retains unresolved escrow");
        eq(1, interruptedRestore.snapshot().accountedItems(), "interrupted escrow retained logically");
        eq(null, interruptedRestore.reserve(), "no new reservation after interrupted publication");
        eq(null, interruptedRestore.cancel(interrupted.token, ENTITY, interrupted.entity, Disposal.CANDIDATE_DISCARDED), "old process token does not authorize refund");
    }

    static void reconciliationAndPickupTransactions() {
        Fixture f = pending();
        DisplacedNeedleLedger restored = DisplacedNeedleLedger.restore(OWNER, f.ledger.toDecimalMap());
        Snapshot expected = restored.snapshot();
        eq(null, restored.recoverUnpublished(expected, 0, true, Disposal.CANDIDATE_DISCARDED), "saved metadata cannot fabricate original escrow");
        eq(null, restored.recoverUnpublished(expected, 1, false, Disposal.CANDIDATE_DISCARDED), "corrupt escrow refuses enhancement/refund");
        eq(null, restored.recoverUnpublished(expected, 1, true, Disposal.UNRESOLVED), "unloaded/missing candidate cannot be refunded");
        eq(null, restored.recoverUnpublished(expected, 1, true, Disposal.NO_CANDIDATE), "pending UUID requires definitive discard");
        ReturnTicket recovered = restored.recoverUnpublished(expected, 1, true, Disposal.CANDIDATE_DISCARDED);
        yes(recovered != null, "verified original escrow with discarded candidate can reconcile");
        eq(1L, recovered.nonce(), "reconciliation cannot mint a new deployment nonce");
        eq(null, restored.recoverUnpublished(expected, 1, true, Disposal.CANDIDATE_DISCARDED), "snapshot replay cannot issue another return");
        DisplacedNeedleLedger queuedRefund = DisplacedNeedleLedger.restore(OWNER, restored.toDecimalMap());
        eq(Custody.RETURN_PENDING, queuedRefund.snapshot().custody(), "unstarted refund preserves exact escrow custody");
        ReturnTicket resumed = queuedRefund.recoverUnpublished(queuedRefund.snapshot(), 1, true, Disposal.NO_CANDIDATE);
        yes(resumed != null, "unstarted original escrow refund can resume");
        yes(queuedRefund.beginReturn(resumed), "refund transfer begins");
        DisplacedNeedleLedger ambiguous = DisplacedNeedleLedger.restore(OWNER, queuedRefund.toDecimalMap());
        eq(State.LOST, ambiguous.snapshot().state(), "interrupted native inventory insertion is ambiguous");
        eq(null, ambiguous.recoverUnpublished(ambiguous.snapshot(), 1, true, Disposal.NO_CANDIDATE), "ambiguous refund cannot auto retry");
        no(ambiguous.beginReturn(resumed), "old refund ticket cannot cross restore");

        Fixture live = active(); DisplacedNeedleLedger l = live.ledger;
        yes(l.isBound(OWNER, 1, ENTITY, live.entity), "read-only exact binding proof");
        no(l.isBound(OWNER, 1, ENTITY, new Object()), "read-only proof still excludes clone");
        PickupTicket first = l.beginPickup(OWNER, 1, ENTITY, live.entity);
        yes(first != null, "pickup transfers native original into guarded pickup escrow");
        eq(null, l.beginPickup(OWNER, 1, ENTITY, live.entity), "native pickup callback cannot reenter");
        no(l.mayDamage(live.attack, OWNER, 1, ENTITY, live.entity), "pickup retires old attack before callback");
        eq(null, l.reserve(), "in-progress native pickup blocks deployment");
        eq(null, l.beginReprojection(OWNER, 1, ENTITY, live.entity), "in-progress pickup blocks reprojection");
        DisplacedNeedleLedger pickupCrash = DisplacedNeedleLedger.restore(OWNER, l.toDecimalMap());
        eq(State.LOST, pickupCrash.snapshot().state(), "native pickup crash cannot replay insertion");
        no(pickupCrash.finishPickup(first, true), "pickup capability cannot cross restore");
        yes(l.finishPickup(first, false), "full inventory keeps original native item");
        eq(State.ACTIVE, l.snapshot().state(), "failed pickup restores native custody only");
        no(l.mayDamage(live.attack, OWNER, 1, ENTITY, live.entity), "failed pickup cannot revive stale attack");
        PickupTicket second = l.beginPickup(OWNER, 1, ENTITY, live.entity);
        yes(second != null, "same native item may be picked up later");
        no(l.finishPickup(first, true), "old pickup ticket cannot settle later transfer");
        yes(l.finishPickup(second, true), "confirmed original item pickup settles ledger");
        no(l.finishPickup(second, true), "native pickup completion replay refused");
        eq(State.SPENT, l.snapshot().state(), "pickup settles without another refund");
        accounted(l);
        Fixture unknown = pending();
        unknown.ledger.cancel(unknown.token, ENTITY, unknown.entity, Disposal.UNRESOLVED);
        eq(null, unknown.ledger.beginPickup(OWNER, 1, ENTITY, unknown.entity), "never-active candidate cannot gain native pickup rights");
        Fixture dying = active();
        PickupTicket deathRace = dying.ledger.beginPickup(OWNER, 1, ENTITY, dying.entity);
        dying.ledger.ownerDied();
        eq(State.PICKING_UP, dying.ledger.snapshot().state(), "death inside inventory callback retains pickup barrier");
        eq(null, dying.ledger.beginPickup(OWNER, 1, ENTITY, dying.entity), "death cannot reopen nested pickup");
        yes(dying.ledger.finishPickup(deathRace, false), "failed pickup still settles after reentrant death");
        eq(State.LOST, dying.ledger.snapshot().state(), "failed death-race pickup preserves outstanding original");
        PickupTicket deathRetry = dying.ledger.beginPickup(OWNER, 1, ENTITY, dying.entity);
        yes(deathRetry != null, "settled failure permits later native original pickup");
        dying.ledger.ownerDied();
        yes(dying.ledger.finishPickup(deathRetry, true), "confirmed whole pickup after reentrant death settles once");
        eq(State.SPENT, dying.ledger.snapshot().state(), "death-race success cannot leave pickable entity custody");
    }

    static void blocked(Map<String,String> fields, String why) {
        DisplacedNeedleLedger restored = DisplacedNeedleLedger.restore(OWNER, fields);
        eq(State.BLOCKED, restored.snapshot().state(), why);
        eq(null, restored.reserve(), "corrupt data cannot fall back to empty");
        eq(0, restored.snapshot().accountedItems(), "corrupt metadata fabricates no inventory");
        restored.ownerDied(); eq(State.BLOCKED, restored.snapshot().state(), "death cannot clear corruption");
    }
    static void serializationAndBounds() {
        blocked(null, "missing map rejected by restore; fresh state is explicit");
        Fixture f = active(); Map<String,String> valid = f.ledger.toDecimalMap();
        for (String key : valid.keySet()) {
            Map<String,String> changed = new HashMap<>(valid); changed.remove(key); blocked(changed, "missing field " + key);
            changed = new HashMap<>(valid); changed.put(key, null); blocked(changed, "null field " + key);
        }
        Map<String,String> extra = new HashMap<>(valid); extra.put("futureFeature", "1"); blocked(extra, "unknown key rejected");
        String[][] bad = {{"version","2"},{"owner",OTHER.toString()},{"owner","1-1-1-1-1"},
            {"entity","bad"},{"entity",""},{"nonce","0"},{"nonce","-1"},{"nonce","01"},
            {"nonce","+1"},{"nonce"," 1"},{"nonce","9223372036854775808"},
            {"generation","0"},{"generation","-1"},{"generation","0001"},
            {"removedItems","2"},{"removedItems","01"},{"state","NEW_FUTURE_STATE"},
            {"state","EMPTY"},{"state","RESERVED"},{"state","RETURNING"},{"custody","ESCROW"},
            {"custody","RETURNED"},{"custody","NONE"},{"custody","UNKNOWN"}};
        for (String[] row : bad) {
            Map<String,String> changed = new HashMap<>(valid); changed.put(row[0], row[1]); blocked(changed, row[0] + " invalid " + row[1]);
        }
        Map<String,String> ceiling = new HashMap<>(valid); ceiling.put("generation", Long.toString(Long.MAX_VALUE));
        DisplacedNeedleLedger capped = DisplacedNeedleLedger.restore(OWNER, ceiling);
        yes(capped.bindLive(OWNER, 1, ENTITY, f.entity, true), "max generation identity still valid");
        eq(null, capped.beginReprojection(OWNER, 1, ENTITY, f.entity), "generation exhaustion fails closed");
        eq(Long.MAX_VALUE, capped.snapshot().generation(), "generation never wraps");
        ceiling.put("nonce", Long.toString(Long.MAX_VALUE)); ceiling.put("state", "SPENT"); ceiling.put("custody", "CONSUMED");
        capped = DisplacedNeedleLedger.restore(OWNER, ceiling);
        eq(State.SPENT, capped.snapshot().state(), "maximum nonce saved normally");
        eq(null, capped.reserve(), "nonce exhaustion never wraps/reuses");
        accounted(capped);
    }

    static int races(Callable<Boolean> attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 32; i++) results.add(pool.submit(() -> { go.await(); return attempt.call(); }));
        go.countDown(); int won = 0;
        for (Future<Boolean> result : results) if (result.get()) won++;
        pool.shutdown(); yes(pool.awaitTermination(10, TimeUnit.SECONDS), "threads stopped");
        return won;
    }
    static void concurrency() throws Exception {
        DisplacedNeedleLedger l = new DisplacedNeedleLedger(OWNER);
        eq(1, races(() -> l.reserve() != null), "only one concurrent reservation succeeds");
        Fixture f = active();
        eq(1, races(() -> f.ledger.claimDamage(f.attack, OWNER, 1, ENTITY, f.entity)), "one concurrent damage callback");
        Fixture pending = pending();
        ReturnTicket r = pending.ledger.cancel(pending.token, ENTITY, pending.entity, Disposal.CANDIDATE_DISCARDED);
        eq(1, races(() -> pending.ledger.beginReturn(r)), "one concurrent inventory insertion permit");
        eq(1, races(() -> pending.ledger.finishReturn(r, true)), "one concurrent refund commit");
        accounted(pending.ledger);
        Fixture nativePickup = active();
        AtomicReference<PickupTicket> pickup = new AtomicReference<>();
        eq(1, races(() -> {
            PickupTicket acquired = nativePickup.ledger.beginPickup(OWNER, 1, ENTITY, nativePickup.entity);
            if (acquired != null) pickup.set(acquired);
            return acquired != null;
        }), "one concurrent native pickup permit");
        eq(1, races(() -> nativePickup.ledger.finishPickup(pickup.get(), true)), "one concurrent native pickup completion");
    }

    static void adversarialSequences() {
        Random random = new Random(311L);
        for (int run = 0; run < 400; run++) {
            DisplacedNeedleLedger l = new DisplacedNeedleLedger(OWNER);
            Token token = null; ReturnTicket refund = null; AttackPermit attack = null; PickupTicket pickup = null;
            Object entity = new Object();
            boolean everActivated = false;
            for (int step = 0; step < 80; step++) {
                switch (random.nextInt(18)) {
                    case 0 -> { Token fresh = l.reserve(); if (fresh != null) { token = fresh; everActivated = false; } }
                    case 1 -> l.escrow(token, random.nextInt(3), random.nextBoolean());
                    case 2 -> l.publishPending(token, ENTITY, entity);
                    case 3 -> { AttackPermit fresh = l.activate(token, ENTITY, entity, random.nextBoolean(), random.nextBoolean()); if (fresh != null) { attack = fresh; everActivated = true; } }
                    case 4 -> { ReturnTicket fresh = l.cancel(token, ENTITY, entity, Disposal.values()[random.nextInt(3)]); if (fresh != null) { yes(!everActivated, "never refund any activated generation"); refund = fresh; } }
                    case 5 -> l.beginReturn(refund);
                    case 6 -> l.finishReturn(refund, random.nextBoolean());
                    case 7 -> { AttackPermit fresh = l.beginReprojection(OWNER, l.snapshot().nonce(), ENTITY, entity); if (fresh != null) attack = fresh; }
                    case 8 -> l.completeReprojection(attack, random.nextBoolean(), random.nextBoolean());
                    case 9 -> l.claimDamage(attack, OWNER, l.snapshot().nonce(), ENTITY, entity);
                    case 10 -> l.unload(OWNER, l.snapshot().nonce(), ENTITY, entity);
                    case 11 -> l.bindLive(OWNER, l.snapshot().nonce(), ENTITY, entity, random.nextBoolean());
                    case 12 -> l.ownerDied();
                    case 13 -> l.settleEntity(OWNER, l.snapshot().nonce(), ENTITY, entity, Resolution.values()[random.nextInt(3)]);
                    case 14 -> l.abortReprojection(attack);
                    case 15 -> { PickupTicket fresh = l.beginPickup(OWNER, l.snapshot().nonce(), ENTITY, entity); if (fresh != null) pickup = fresh; }
                    case 16 -> l.finishPickup(pickup, random.nextBoolean());
                    case 17 -> l.retireAttack();
                }
                accounted(l);
                DisplacedNeedleLedger read = DisplacedNeedleLedger.restore(OWNER, l.toDecimalMap());
                no(read.snapshot().state() == State.BLOCKED, "every reachable snapshot is consistent");
                eq(l.snapshot().removedItems(), read.snapshot().removedItems(), "decode never changes debit");
                eq(l.snapshot().nonce(), read.snapshot().nonce(), "decode preserves monotonic nonce");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        reservationAndIdentity(); cancelledPublicationAndRefund(); reprojectionAndNativeDisposition();
        unloadReloadAndDeath(); reconciliationAndPickupTransactions(); serializationAndBounds(); concurrency(); adversarialSequences();
        System.out.println("PASS|8|" + assertions);
    }
}
'''


def toolchain(root):
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']))
    if shutil.which('javac'):
        candidates.append(Path(shutil.which('javac')).resolve().parent.parent)
    for parent in [root, *root.parents]:
        candidates.extend(sorted((parent / 'aca-tools').glob('jdk*')))
        candidates.extend(sorted((parent / '.tools').glob('jdk*')))
    for home in candidates:
        if (home / 'bin/javac').is_file() and (home / 'bin/java').is_file():
            return home
    raise RuntimeError('Set JAVA_HOME to a JDK 21 installation')


def run(command):
    result = subprocess.run([str(value) for value in command], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout.strip()


def main():
    root = Path(__file__).resolve().parents[1]
    source = root / 'src/main/java/cn/academy/port/highpower/core/DisplacedNeedleLedger.java'
    java = toolchain(root)
    with tempfile.TemporaryDirectory(prefix='new311-ledger-') as temporary:
        work = Path(temporary)
        harness = work / 'DisplacedNeedleLedgerOracle.java'
        harness.write_text(HARNESS)
        run([java / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-d', work, source, harness])
        result = run([java / 'bin/java', '-cp', work, 'DisplacedNeedleLedgerOracle']).split('|')
    if result[0] != 'PASS':
        raise RuntimeError(str(result))
    print(json.dumps({'result': 'PASS', 'groups': int(result[1]), 'assertions': int(result[2]),
                      'productionSource': str(source.relative_to(root)),
                      'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                      'nativeMinecraftRuntime': False,
                      'crossFileCrashAtomicityClaimed': False}, indent=2))


if __name__ == '__main__':
    main()
