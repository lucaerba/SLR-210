package com.example.synod.message;

public class Read {
    private final int ballot;

    public Read(int ballot) {
        this.ballot = ballot;
    }

    
    /** 
     * @return int
     */
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
