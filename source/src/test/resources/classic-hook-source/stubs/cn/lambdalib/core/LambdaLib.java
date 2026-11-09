package cn.lambdalib.core;public class LambdaLib{public static final Log log=new Log();public static class Log{public void error(String s){throw new AssertionError(s);}}}
