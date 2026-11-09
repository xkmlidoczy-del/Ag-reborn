package cn.academy.port.client.terminal.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Local external-media boundary. Nothing is downloaded or copied from the classic track catalog. */
public final class MediaFiles {
    public record Track(String id, Path source, Path cover, double lengthSeconds) {}
    public record Scan(List<Track> tracks, List<String> warnings) {}
    public record VorbisInfo(int sampleRate, int channels, long samples) {
        public double seconds() { return (double) samples / sampleRate; }
    }
    public static final long MAX_AUDIO_BYTES = 512L * 1024 * 1024;
    private MediaFiles() {}

    public static String classicId(String fileName) { int dot=fileName.indexOf('.'); return dot<0?fileName:fileName.substring(0,dot); }
    public static Scan scan(Path gameDirectory) throws IOException {
        Path root=gameDirectory.toAbsolutePath().normalize().resolve("acmedia");
        createDirectory(root); Path sources=root.resolve("source"), covers=root.resolve("cover"); createDirectory(sources);createDirectory(covers);
        Path readme=root.resolve("README.txt");
        if (!Files.exists(readme,LinkOption.NOFOLLOW_LINKS)) Files.writeString(readme,
            "AcademyCraft Media Player\nAdd your own Ogg Vorbis audio to acmedia/source/<id>.ogg.\nOptional user-provided PNG covers: acmedia/cover/<id>.png.\nThe classic ID is the filename prefix before the first dot. Names, descriptions and volume are saved in metadata.properties.\nThis port deliberately includes no internal songs, music covers or song-item art.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        List<Track> tracks=new ArrayList<>();List<String>warnings=new ArrayList<>();Set<String>ids=new HashSet<>();
        List<Path> paths;try(var listed=Files.list(sources)){paths=listed.sorted(Comparator.comparing(p->p.getFileName().toString())).toList();}
        for(Path p:paths){String file=p.getFileName().toString(); if(!file.endsWith(".ogg"))continue;
            String id=classicId(file);if(id.isEmpty()){warnings.add(file+": empty classic ID");continue;}
            try {
                requireRegular(p);long size=Files.size(p);if(size>MAX_AUDIO_BYTES)throw new IOException("exceeds the 512 MiB audio limit");
                VorbisInfo info=inspectVorbis(p);if(!ids.add(id)){warnings.add(file+": duplicate classic ID "+id);continue;}
                Path cover=covers.resolve(id+".png"); if(!safeCover(cover)){if(Files.exists(cover,LinkOption.NOFOLLOW_LINKS))warnings.add(id+": invalid/oversized cover; using missing-cover icon");cover=null;}
                tracks.add(new Track(id,p,cover,info.seconds()));
            }catch(IOException|RuntimeException ex){warnings.add(file+": "+ex.getMessage());}
        }
        return new Scan(List.copyOf(tracks),List.copyOf(warnings));
    }
    static void createDirectory(Path p)throws IOException{if(Files.isSymbolicLink(p))throw new IOException("Symbolic-link media directory is unsupported: "+p.getFileName());Files.createDirectories(p);if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Not a media directory");}
    static void requireRegular(Path p)throws IOException{if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p))throw new IOException("Not a regular local media file");}
    static void requireDirectory(Path p)throws IOException{if(Files.isSymbolicLink(p)||!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Media directory changed or is a symbolic link");}
    static void requireSource(Path p)throws IOException{Path source=p.toAbsolutePath().normalize().getParent();if(source==null||!source.getFileName().toString().equals("source")||source.getParent()==null||!source.getParent().getFileName().toString().equals("acmedia"))throw new IOException("Audio is outside acmedia/source");requireDirectory(source.getParent());requireDirectory(source);requireRegular(p);}
    public static boolean safeCover(Path p){
        try{Path parent=p.toAbsolutePath().normalize().getParent();if(parent==null||!parent.getFileName().toString().equals("cover")||parent.getParent()==null||!parent.getParent().getFileName().toString().equals("acmedia"))return false;requireDirectory(parent.getParent());requireDirectory(parent);requireRegular(p);if(Files.size(p)>16L*1024*1024)return false;
            try(var input=ImageIO.createImageInputStream(p.toFile())){if(input==null)return false;var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return false;var reader=readers.next();try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);return reader.getFormatName().equalsIgnoreCase("png")&&w>0&&h>0&&w<=2048&&h<=2048;}finally{reader.dispose();}}
        }catch(IOException|RuntimeException ex){return false;}
    }
    /** Strict single-logical-stream Ogg framing/CRC and Vorbis duration; playback still uses Minecraft's actual decoder. */
    public static VorbisInfo inspectVorbis(Path p)throws IOException{
        int serial=0,sequence=0,rate=0,channels=0;long finalGranule=-1;boolean first=true,eos=false;ByteArrayOutputStream initial=new ByteArrayOutputStream();
        try(var in=new BufferedInputStream(Files.newInputStream(p))){
            for(;;){byte[] header=in.readNBytes(27);if(header.length==0)break;if(header.length!=27)throw new IOException("Truncated Ogg header");
                if(header[0]!='O'||header[1]!='g'||header[2]!='g'||header[3]!='S'||header[4]!=0)throw new IOException("Invalid Ogg framing");
                int flags=header[5]&255;int pageSerial=le32(header,14),pageSequence=le32(header,18);int count=header[26]&255;byte[]lace=in.readNBytes(count);if(lace.length!=count)throw new IOException("Truncated Ogg lacing");int length=0;for(byte b:lace)length+=b&255;byte[]body=in.readNBytes(length);if(body.length!=length)throw new IOException("Truncated Ogg page");
                int expected=le32(header,22);Arrays.fill(header,22,26,(byte)0);int crc=crc(crc(crc(0,header),lace),body);if(crc!=expected)throw new IOException("Ogg checksum mismatch");
                if(eos)throw new IOException("Chained/multiplexed Ogg is unsupported");
                if(first){if((flags&2)==0||(flags&1)!=0||pageSequence!=0)throw new IOException("Missing Ogg beginning");serial=pageSerial;first=false;}
                else if(pageSerial!=serial||pageSequence!=++sequence||(flags&2)!=0)throw new IOException("Discontinuous/multiplexed Ogg pages");
                if(rate==0){int offset=0;for(byte b:lace){int n=b&255;initial.write(body,offset,n);offset+=n;if(initial.size()>8192)throw new IOException("Oversized identification packet");if(n<255){byte[]ident=initial.toByteArray();if(ident.length!=30||ident[0]!=1||!new String(ident,1,6,StandardCharsets.US_ASCII).equals("vorbis")||le32(ident,7)!=0||(ident[29]&1)==0)throw new IOException("Not Ogg Vorbis");channels=ident[11]&255;rate=le32(ident,12);if(channels<1||channels>2||rate<8000||rate>192000)throw new IOException("Only mono/stereo Vorbis at 8–192 kHz is supported");break;}}
                }
                if((flags&4)!=0){eos=true;finalGranule=le64(header,6);}
            }
        }
        if(first||rate==0||!eos||finalGranule<=0)throw new IOException("Incomplete/empty Ogg Vorbis stream");return new VorbisInfo(rate,channels,finalGranule);
    }
    static int le32(byte[]b,int i){return (b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16)|(b[i+3]<<24);}
    static long le64(byte[]b,int i){return Integer.toUnsignedLong(le32(b,i))|((long)le32(b,i+4)<<32);}
    static int crc(int crc,byte[]data){for(byte value:data){crc^=(value&255)<<24;for(int bit=0;bit<8;bit++)crc=(crc<<1)^((crc<0)?0x04c11db7:0);}return crc;}
}
