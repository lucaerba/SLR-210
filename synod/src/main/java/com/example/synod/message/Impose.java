package com.example.synod.message;

public class Impose {
    int ballot = 0;
    int proposal = 0;

    public Impose(int ballot, int proposal) {
        this.ballot = ballot;
        this.proposal = proposal;

    }

    public int getBallot() {
        return ballot;
    }

    public int getProposal() {
        return proposal;
    }
}
