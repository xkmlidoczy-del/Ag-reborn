package cn.academy.port.macro;

import java.util.*;
import java.util.function.*;

/** Deterministic round-robin work. A tick budget delays work; it never drops the tail of a roster. */
public final class FairWorkQueue {
    private static final class Job {
        final Iterator<? extends Runnable> steps;final BooleanSupplier valid;final Runnable done;
        Job(Collection<? extends Runnable> steps,BooleanSupplier valid,Runnable done){this.steps=List.copyOf(steps).iterator();this.valid=valid;this.done=done;}
    }
    private final ArrayDeque<Job> jobs=new ArrayDeque<>();
    private long tick=Long.MIN_VALUE;private int used;private boolean draining;
    public void add(Collection<? extends Runnable> steps,BooleanSupplier valid,Runnable done){jobs.addLast(new Job(steps,valid,done));}
    public int pending(){return jobs.size();}
    public int used(){return used;}
    public boolean reserve(long now,int budget){if(now!=tick){tick=now;used=0;}if(used>=budget)return false;used++;return true;}
    public void clear(){jobs.clear();}
    public void drain(long now,int budget){
        if(draining)return;if(now!=tick){tick=now;used=0;}draining=true;
        try{while(used<budget&&!jobs.isEmpty()){
            var job=jobs.removeFirst();
            if(!job.valid.getAsBoolean()){job.done.run();continue;}
            if(!job.steps.hasNext()){job.done.run();continue;}
            Runnable step=job.steps.next();used++;step.run();
            if(job.steps.hasNext())jobs.addLast(job);else job.done.run();
        }}finally{draining=false;}
    }
}
