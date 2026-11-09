package org.apache.commons.io;
public final class IOUtils { public static String toString(java.io.Reader reader)throws java.io.IOException{var text=new StringBuilder();var buffer=new char[1024];int read;while((read=reader.read(buffer))!=-1)text.append(buffer,0,read);return text.toString();} }
