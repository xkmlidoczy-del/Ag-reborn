/* AcademyCraft1.0.7 FreqTransmitterUI.State source time boundaries. GPLv3. */
package cn.academy.port.client.terminal;

public final class FrequencyTimeline {
    public static final long ACTION_TIMEOUT=20000,TRANSMIT_TIMEOUT=3000,RETURN_DELAY=700,QUIT_DELAY=1000;
    private FrequencyTimeline(){}
    public static boolean timeout(long elapsed,boolean transmitting){return elapsed>(transmitting?TRANSMIT_TIMEOUT:ACTION_TIMEOUT);}
    public static boolean returnReady(long elapsed){return elapsed>RETURN_DELAY;}
    public static boolean quitReady(long elapsed){return elapsed>QUIT_DELAY;}
    public static boolean allowedCharacter(char c){return c!=167&&c>=32&&c!=127;}
}
