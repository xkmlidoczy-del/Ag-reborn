package com.mojang.blaze3d.shaders;
public final class Uniform{private final String name;public Uniform(String n){name=n;}public void set(float x){proof.Trace.add("uniform:"+name+":"+proof.Trace.f(x));}public void set(float x,float y,float z){proof.Trace.add("uniform:"+name+":"+proof.Trace.f(x)+","+proof.Trace.f(y)+","+proof.Trace.f(z));}}
