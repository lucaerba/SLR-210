package com.example.synod.message;

public class Abort {
    private final int ballot;

    public Abort(int ballot) {
        this.ballot = ballot;
    }

    public int getBallot() {
        return this.ballot;
    }

    @Override
    public String toString() {
        return "ReadMsg{" +
                "ballot=" + ballot +
                '}';
    }
}
