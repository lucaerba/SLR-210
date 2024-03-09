package com.example.synod.message;

public class Gather {
    private final int ballot;
    private final int imposeBallot;
    private final Integer estimate;

    public Gather(int ballot, int imposeBallot, Integer estimate){
        this.ballot = ballot;
        this.imposeBallot = imposeBallot;
        this.estimate = estimate;
    }


    public int getBallot() {
        return ballot;
    }

    public int getImposeBallot() {
        return imposeBallot;
    }

    public Integer getEstimate() {
        return estimate;
    }

    @Override
    public String toString() {
        return "GatherMsg{" +
                "ballot=" + ballot +
                ", imposeBallot=" + imposeBallot +
                ", estimate=" + estimate +
                '}';
    }
}
