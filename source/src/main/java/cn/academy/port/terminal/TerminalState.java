/* AcademyCraft 1.0.7 TerminalData/AppRegistry semantic-ID adaptation. GPLv3. See NOTICE. */
package cn.academy.port.terminal;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Common-safe persistent ledger. Defaults do not acquire saved bits, as in classic TerminalData. */
public final class TerminalState {
    public static final List<String> APPS=List.of("settings","tutorial","skill_tree","freq_transmitter","media_player","urban_communication");
    public static final Set<String> PREINSTALLED=Set.of("settings","tutorial");
    private final Set<String> installed=new LinkedHashSet<>();
    private boolean terminal;
    public boolean terminalInstalled(){return terminal;}
    public static boolean knownApp(String app){return app!=null&&APPS.contains(app);}
    public boolean isInstalled(String app){return knownApp(app)&&(PREINSTALLED.contains(app)||installed.contains(app));}
    public List<String> installedApps(){return APPS.stream().filter(this::isInstalled).toList();}
    public Set<String> savedAppIds(){return Set.copyOf(installed);}
    public boolean installTerminal(){if(terminal)return false;terminal=true;return true;}
    /** The source data method does not itself require a terminal; actual item/packet ingress does. */
    public boolean installApp(String app){return knownApp(app)&&!isInstalled(app)&&installed.add(app);}
    public void restore(boolean terminalInstalled,Collection<String> installedIds){
        terminal=terminalInstalled;installed.clear();for(String id:installedIds)if(id!=null)installed.add(id);
    }
}
