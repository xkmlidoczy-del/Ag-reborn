package cn.academy.port.city;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Brief, local conversations for ordinary residents; service NPCs retain their own tasks and trading. */
final class CityNpcDialogue {
    private CityNpcDialogue() {}
    static Component line(ServerPlayer player, CityNpcEntity npc, int sequence) {
        String group = switch (npc.look()) {
            case STUDENT_BAG, STUDENT_BOOKS, STUDENT_SPORT, STUDENT_BLAZER, STUDENT_CASUAL, ESPER_UNIFORM, ESPER_TRACK -> "student";
            case COMMUTER, OFFICE_STAFF, ESPER_WORK -> "commuter";
            case VISITOR, CASUAL_VISITOR, TRAVELER, ESPER_FIELD -> "visitor";
            case SHOP_CLERK, VENDOR -> "shop";
            case BARISTA -> "barista";
            case RESIDENT -> "resident";
            case RESEARCHER, ESPER_LAB -> "researcher";
            case TEACHER -> "teacher";
            case GUARD -> "guard";
            case TECHNICIAN, ENGINEER -> "technician";
            case ATHLETE -> "athlete";
            case PERFORMER, ESPER_ART -> "performer";
            case AIRPORT_STAFF -> "airport";
            case RAIL_STAFF -> "rail";
            case MEDIC -> "medic";
        };
        boolean night = CityCrowdPlan.Period.at(player.serverLevel().getDayTime()) == CityCrowdPlan.Period.NIGHT;
        boolean food = player.getMainHandItem().getFoodProperties(player) != null
                && (group.equals("student") || group.equals("visitor") || group.equals("resident"));
        String line = food ? "food" : night ? "night" : Integer.toString(Math.floorMod(sequence, 2));
        return Component.translatable("academy.city.talk." + (food ? "shared" : group) + "." + line);
    }
}
