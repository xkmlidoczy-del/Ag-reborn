package com.mojang.blaze3d.shaders;

/** Native descriptor numbering from pinned NeoForge sources; buffer uploads remain outside this CPU proof. */
public final class Uniform {
    public static final int UT_FLOAT1=4,UT_FLOAT4=7,UT_MAT4=10;
    private final String name;
    private final int type,count;
    public Uniform(String name,int type,int count){this.name=name;this.type=type;this.count=count;}
    public int getType(){return type;}
    public int getCount(){return count;}
    public static int getTypeFromString(String type){return switch(type){case "int"->0;case "float"->4;case "matrix2x2"->8;case "matrix3x3"->9;case "matrix4x4"->10;default->throw new AssertionError("undeclared uniform type "+type);};}
    private void floatWrite(int needed){if(type<4)throw new NullPointerException("integer uniform has no floatValues buffer: "+name);if(count<needed)throw new IndexOutOfBoundsException("uniform float buffer too small: "+name);}
    public void set(float value){floatWrite(1);proof.Trace.add("uniform:"+name+":"+proof.Trace.f(value));}
    public void set(float a,float b,float c){floatWrite(3);proof.Trace.add("uniform:"+name+":"+proof.Trace.f(a)+","+proof.Trace.f(b)+","+proof.Trace.f(c));}
}
