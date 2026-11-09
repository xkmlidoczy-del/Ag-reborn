package cn.academy.port.client.terminal.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Packaged immutable source/media witnesses. No .reference, staging or current working directory is read. */
final class MediaSourceFixtures {
    static final String ROOT="classic-oracles/terminal-media/";
    static final String MANIFEST_SHA256="e390954e126c989f8ee25c574149a4cc75e2b1af40700af82141bc309db99377";
    static byte[] bytes(String name)throws Exception{try(var input=MediaSourceFixtures.class.getClassLoader().getResourceAsStream(ROOT+name)){if(input==null)throw new AssertionError("Missing packaged witness "+name);return input.readAllBytes();}}
    static String text(String name)throws Exception{return new String(bytes(name),StandardCharsets.UTF_8);}
    static String sha(byte[]bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void verifyAll()throws Exception{byte[]manifest=bytes("manifest.json");if(!sha(manifest).equals(MANIFEST_SHA256))throw new AssertionError("Pinned media manifest changed");var matcher=Pattern.compile("\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"[^}]*?\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"").matcher(new String(manifest,StandardCharsets.UTF_8));int count=0;while(matcher.find()){count++;if(!sha(bytes(matcher.group(1))).equals(matcher.group(2)))throw new AssertionError("Witness changed: "+matcher.group(1));}if(count<20)throw new AssertionError("Incomplete media witness manifest");
        for(String path:new String[]{"guis/media_player.xml","guis/media_player_aux.xml","textures/guis/apps/media_player/back.png","textures/guis/apps/media_player/play.png","textures/guis/apps/media_player/pause.png","textures/guis/apps/media_player/stop.png","textures/guis/icons/edit.png","textures/guis/icons/volume_overlay.png","textures/guis/icons/icon_nomedia.png"}){try(var input=MediaSourceFixtures.class.getClassLoader().getResourceAsStream("assets/academy/"+path)){if(input==null||!java.util.Arrays.equals(input.readAllBytes(),bytes("assets/academy/"+path)))throw new AssertionError("Promoted media resource differs: "+path);}}
    }
}
