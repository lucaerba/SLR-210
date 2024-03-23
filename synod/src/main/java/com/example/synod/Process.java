package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.example.synod.message.*;
import scala.Int;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private boolean faultProneMode = false;
    private boolean silentMode = false;

    private int n;//number of processes
    private int i;//id of current process
    private ArrayList<ActorRef> processes;//other processes' references
    private int proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    private boolean hold ;
    private HashMap<ActorRef, State> states;
    private int nAck = 0;
    private final double alpha;

    /**
     * Static method to create an actor
     */
    public static Props createActor(int n, int i, float alpha) {
        return Props.create(Process.class, () -> new Process(n, i, alpha));
    }

    public Process(int n, int i, float alpha) {
        this.n = n;
        this.i = i;
        this.ballot = i-n;
        this.readBallot = 0;
        this.imposeBallot = i-n;
        this.estimate = -1;
        this.states = new HashMap<ActorRef, State>();;
        this.hold = false;
        this.alpha = alpha;
    }

    /*
    Once process i receives a launch message, it picks an input value, randomly chosen in {0, 1} and invokes instances of propose operation
    with this value until a value is decided. As a basis, one can use the OFC pseudocode discussed in the lecture (adjusted to be used within AKKA).
     */
    private void propose(int v) {
        log.info(this + " - propose("+ v+")");
        proposal = v;
        ballot += n;
        this.states.clear();

        for (ActorRef actor : this.processes) {
            if (actor.equals(getSelf())) {
                continue;
            }
            actor.tell(new Read(this.ballot), this.getSelf());
            this.log.info("Read ballot " + this.ballot + " msg: " + this + " -> " + actor.toString());
        }
    }

    public void onReceive(Object message) throws Throwable {
        //Silent Mode
        if(this.silentMode){
            return;
        }else if(this.faultProneMode){//Fault Prone Mode
            boolean crash = (((new Random()).nextFloat())<this.alpha);
            if(crash){
                this.silentMode = true;
                return;
            }
        }

        if (message instanceof Membership) {
            log.info(this + " - membership received");
            Membership m = (Membership) message;
            processes = (ArrayList<ActorRef>) m.references;

        } else if (message instanceof Launch) {
            if(this.hold) return;

            log.info(this + " - launch received");

            Random random = new Random();
            int n = random.nextInt()%2 ;
            propose(n);

        } else if (message instanceof Read){
            log.info(this + " - read received");
            readHandler((Read) message, getSender());

        } else if (message instanceof Abort) {
            log.info(this + " - abort received");
            abortHandler((Abort) message, getSender());
            
        }else if (message instanceof Gather){
            log.info(this + " - gather received");
            gatherHandler((Gather) message, getSender());

        }else if (message instanceof Impose){
            log.info(this + " - impose received");
            imposeHandler((Impose) message, getSender());

        }else if (message instanceof Ack){
            log.info(this + " - ack received");
            ackHandler((Ack) message, getSender());

        }else if (message instanceof Decide){
            log.info(this + " - decide received");
            decideHandler((Decide) message, getSender());

        }else if (message instanceof Crash){
            log.info(this + " - crash received");
            this.faultProneMode = true;
        }else if(message instanceof Hold){
            log.info(this + " - hold received");
            this.hold = true;
        }
    }

    private void decideHandler(Decide message, ActorRef sender) {
        int v = message.getV();

        this.silentMode = true; // Once we decided on a value, we stop listening (for better logs)
        long time = System.currentTimeMillis() - Main.startTime;
        log.info(this  + " decided received from p" + getSender() + " (value=" + v + ")" + " | time: " + time);

        for(ActorRef actor : this.processes){
            actor.tell(new Decide(v), getSelf());
        }
    }

    private void ackHandler(Ack message, ActorRef sender) {
        this.nAck++;

        if(this.nAck >= (this.n/2)){
            this.nAck = 0;
            log.info(this + " received ACK from a majority" + " (b=" + ballot + ")");
            for(ActorRef actor : this.processes){
                actor.tell(new Decide(this.proposal), getSelf());
            }
        }
    }

    private void imposeHandler(Impose message, ActorRef sender) {
        int newBallot = message.getBallot();
        int v = message.getProposal();

        if((this.readBallot > newBallot || this.imposeBallot > newBallot)&(!this.hold)){
            this.log.info(this + " - sending ABORT message (" + newBallot + ") to " + sender.path().name());
            sender.tell(new Abort(newBallot), getSelf());

        }else{
            this.estimate = v;
            this.imposeBallot = newBallot;

            this.log.info(this + " - sending ACK message (" + newBallot + ") to " + sender.path().name());
            sender.tell(new Ack(newBallot), getSelf());
            
        }
    }

    private void gatherHandler(Gather message, ActorRef sender) {
        int estBallot = message.getEstimate();
        int newBallot = message.getBallot();
        int estimate = message.getImposeBallot();

        this.states.put(sender, new State(estimate, estBallot));

        //  If we get at least n/2 states (majority)
        if (this.states.size() >= this.n/2){
            int highestEstBallot = 0;
            Integer proposal = 0;

            for (State s : this.states.values()){
                if (s.getEstballot() > highestEstBallot){
                    highestEstBallot = s.getEstballot();
                    proposal = s.getEst();
                }
            }

            if (proposal > 0){
                this.proposal = proposal;
            }

            this.states.clear();
            for (ActorRef actor : this.processes){
                actor.tell(new Impose(newBallot, this.proposal), getSelf());

            }
        }
    }

    private void abortHandler(Abort message, ActorRef sender) {
        return;
    }

    private void readHandler(Read r, ActorRef sender){
        int newBallot = r.getBallot();

        if((this.readBallot > newBallot || this.imposeBallot > newBallot) & (!this.hold)){
            this.log.info(this + " - sending ABORT message (" + newBallot + ") to "+ sender.path().name());
            sender.tell(new Abort(newBallot), this.getSelf());

        }else{
            this.log.info(this + " - sending GATHER message (" + newBallot + ", " + this.imposeBallot + ", " + this.estimate+") to " + sender.path().name());
            this.readBallot = newBallot;
            sender.tell(new Gather(newBallot, this.imposeBallot, this.estimate), this.getSelf());

        }
    }

    @Override
    public String toString() {
        return "Process #" + i;
    }

    static class State {
        private Integer est;
        private int estballot;

        public State(Integer est, int estballot) {
            this.est = est;
            this.estballot = estballot;
        }

        public Integer getEst(){return this.est;}

        public int getEstballot(){return this.estballot;}
    }
}
