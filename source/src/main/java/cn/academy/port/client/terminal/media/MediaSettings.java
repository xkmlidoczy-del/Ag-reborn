package cn.academy.port.client.terminal.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Source external <id>_name/<id>_desc defaults and volume, persisted locally without a server or catalog. */
public final class MediaSettings {
    private final Path path;private final Properties values=new Properties();
    public MediaSettings(Path gameDirectory)throws IOException{Path root=gameDirectory.toAbsolutePath().normalize().resolve("acmedia");MediaFiles.createDirectory(root);path=root.resolve("metadata.properties");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){MediaFiles.requireRegular(path);if(Files.size(path)>1024*1024)throw new IOException("Media metadata exceeds 1 MiB");try(var r=Files.newBufferedReader(path,StandardCharsets.UTF_8)){values.load(r);}}}
    public synchronized String name(String id){return values.getProperty(id+"_name",id);}
    public synchronized String description(String id){return values.getProperty(id+"_desc",id);}
    public synchronized float volume(){try{return clamp(Float.parseFloat(values.getProperty("volume","1.0")));}catch(NumberFormatException ex){return 1;}}
    public synchronized void name(String id,String value)throws IOException{put(id+"_name",value);}
    public synchronized void description(String id,String value)throws IOException{put(id+"_desc",value);}
    public synchronized void volume(float volume)throws IOException{put("volume",Float.toString(clamp(volume)));}
    private void put(String key,String value)throws IOException{if(value.length()>2048)throw new IOException("Media metadata text exceeds 2048 characters");String previous=values.getProperty(key);values.setProperty(key,value);try{save();}catch(IOException ex){if(previous==null)values.remove(key);else values.setProperty(key,previous);throw ex;}}
    private void save()throws IOException{MediaFiles.requireDirectory(path.getParent());if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))MediaFiles.requireRegular(path);Path tmp=Files.createTempFile(path.getParent(),"metadata-",".tmp");try{try(var w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){values.store(w,"AcademyCraft external media metadata; no bundled tracks");}try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    public static float clamp(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):1;}
}
