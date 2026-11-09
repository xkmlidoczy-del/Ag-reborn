package cn.academy.port.city.defense;

/** Shared semantic moves; timing is authoritative server ticks (20 Hz). */
public enum O5Moves {
    JAB("踏步直拳",11,4,8,0), CROSS("拧髋重拳",13,5,10,0),
    PUSH("沉身推掌",16,6,9,1), PULL("退步擒拉",14,5,5,2),
    FLICK("转肩弹指",12,5,12,3), ELBOW("旋身横肘",13,5,11,0),
    KNEE("支撑提膝",16,6,13,4), SIDE_KICK("移重侧踢",19,8,15,4),
    STOMP_KICK("后仰正蹬",19,8,18,4), DOUBLE_PALM("沉蓄双掌",22,9,22,1);
    public final String title; public final int ticks,hit;public final float damage;public final int impulse;
    O5Moves(String title,int ticks,int hit,float damage,int impulse){this.title=title;this.ticks=ticks;this.hit=hit;this.damage=damage;this.impulse=impulse;}
    public static O5Moves of(int id){return values()[Math.floorMod(id,values().length)];}
}
