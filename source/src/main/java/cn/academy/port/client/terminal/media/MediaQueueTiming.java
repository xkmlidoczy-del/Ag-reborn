package cn.academy.port.client.terminal.media;

import java.util.ArrayDeque;

/** Tracks actual decoded buffer durations as OpenAL unqueues them; AL_SEC_OFFSET is relative to the remaining queue. */
public final class MediaQueueTiming {
    private final ArrayDeque<Double> durations=new ArrayDeque<>();private double removed;
    public void synchronize(int nativeQueued){if(nativeQueued<0||nativeQueued>durations.size())throw new IllegalStateException("Native audio queue changed unexpectedly");while(durations.size()>nativeQueued)removed+=durations.removeFirst();}
    public void queued(int pcmBytes,int frameBytes,float rate){if(pcmBytes<0||frameBytes<=0||rate<=0)throw new IllegalArgumentException("Invalid PCM format");durations.addLast((double)pcmBytes/frameBytes/rate);}
    public double seconds(int nativeQueued,double queueOffset){synchronize(nativeQueued);return removed+Math.max(0,queueOffset);}
}
