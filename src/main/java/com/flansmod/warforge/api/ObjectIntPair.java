package com.flansmod.warforge.api;

public class ObjectIntPair<T> {
    private T left;
    private int right;

    public ObjectIntPair() {
        left = null;
        right = 0;
    }

    public ObjectIntPair(T left, int right) {
        this.left = left;
        this.right = right;
    }

    public void setLeft(T left) { this.left = left; }
    public T getLeft() { return left; }

    public void setRight(int right) { this.right = right; }
    public int getRight() { return right; }

}
