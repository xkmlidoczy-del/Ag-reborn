package cn.lambdalib.s11n.network;
public class Future<T>{public T result;public int sends;public void sendResult(T value){result=value;sends++;}}
