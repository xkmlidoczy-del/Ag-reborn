package cn.lambdalib.util.generic;public class RandUtils {public static int chosen;public static int rangei(int min,int max){return min+Math.floorMod(chosen,max-min);}}
