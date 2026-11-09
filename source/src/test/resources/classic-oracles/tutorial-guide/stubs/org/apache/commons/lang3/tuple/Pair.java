package org.apache.commons.lang3.tuple;
public final class Pair<L,R> { private final L left;private final R right;private Pair(L l,R r){left=l;right=r;} public static <L,R>Pair<L,R> of(L l,R r){return new Pair<>(l,r);} public L getLeft(){return left;}public R getRight(){return right;} }
