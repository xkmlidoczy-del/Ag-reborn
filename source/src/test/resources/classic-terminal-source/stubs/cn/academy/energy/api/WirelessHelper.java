package cn.academy.energy.api;
import java.util.*;import cn.academy.energy.api.block.IWirelessMatrix;import cn.academy.energy.internal.WirelessNet;public class WirelessHelper {public static final Map<IWirelessMatrix,WirelessNet> networks=new IdentityHashMap<>();public static WirelessNet getWirelessNet(IWirelessMatrix matrix){return networks.get(matrix);}}
