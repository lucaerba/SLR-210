package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

import com.example.synod.message.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    
    private final boolean DEBUG = false ;

    private int n;//number of processes
    private int i;//id of current process
    private final double alpha;

    private ArrayList<ActorRef> processes;//other processes' references
    private int proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    private HashMap<ActorRef, State> states;
    private int nAck = 0;

    private boolean hold;
    private boolean faultProneMode = false;
    private boolean silentMode = false;
    private int decidedValue = -1;
    /**
     * Static method to create an actor
     */
    public static Props createActor(int n, int i, double alpha) {
        return Props.create(Process.class, () -> new Process(n, i, alpha));
    }

    public Process(int n, int i, double alpha) {
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
        if(DEBUG) log.info(this + " - propose("+ v+")");
        proposal = v;
        ballot += n;
        this.states.clear();

        for (ActorRef actor : this.processes) {
            if (actor.equals(getSelf())) {
                continue;
            }
            actor.tell(new Read(this.ballot), this.getSelf());
            if(DEBUG) this.log.info("Read ballot " + this.ballot + " msg: " + this + " -> " + actor.toString());
        }
    }

    public void onReceive(Object message) throws Throwable {
        //Silent Mode
        if(this.silentMode){
            return;
        }else if(this.faultProneMode){//Fault Prone Mode
            boolean crash = (((new Random()).nextDouble()) < this.alpha);
            if(crash){
                this.silentMode = true;
                return;
            }
        }

        if (message instanceof Membership) {
            if(DEBUG) log.info(this + " - membership received");

            Membership m = (Membership) message;
            processes = (ArrayList<ActorRef>) m.references;

        } else if (message instanceof Launch) {
            if(this.hold) return;

            if(DEBUG) log.info(this + " - launch received");

                // Pick a random value (0 or 1
                if(this.decidedValue == -1){
                    Random random = new Random();
                    boolean b = random.nextBoolean();
                    if (b) {
                        this.decidedValue = 1;
                    } else {
                        this.decidedValue = 0;
                    }
                }
                propose(this.decidedValue);
                getContext().system().scheduler().scheduleOnce(Duration.create(50, TimeUnit.MILLISECONDS), getSelf(), new Launch(), getContext().system().dispatcher(), null);

        } else if (message instanceof Read){
            if(DEBUG) log.info(this + " - read received");
            readHandler((Read) message, getSender());

        } else if (message instanceof Abort) {
            if(DEBUG) log.info(this + " - abort received");
            abortHandler((Abort) message, getSender());
            
        }else if (message instanceof Gather){
            if(DEBUG) log.info(this + " - gather received");
            gatherHandler((Gather) message, getSender());

        }else if (message instanceof Impose){
            if(DEBUG) log.info(this + " - impose received");
            imposeHandler((Impose) message, getSender());

        }else if (message instanceof Ack){
            if(DEBUG) log.info(this + " - ack received");
            ackHandler((Ack) message, getSender());

        }else if (message instanceof Decide){
            if(DEBUG) log.info(this + " - decide received");
            decideHandler((Decide) message, getSender());

        }else if (message instanceof Crash){
            if(DEBUG) log.info(this + " - crash received");
            this.faultProneMode = true;

        }else if(message instanceof Hold){
            if(DEBUG) log.info(this + " - hold received");
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

        if(this.nAck > (this.n/2)){
            this.nAck = 0;
            if(DEBUG) log.info(this + " received ACK from a majority" + " (b=" + ballot + ")");
            for(ActorRef actor : this.processes){
                
                actor.tell(new Decide(this.proposal), getSelf());
            }
            getContext().getSystem().scheduler().scheduleOnce(
                    Duration.create(100, TimeUnit.MILLISECONDS),
                    () -> {
                        getContext().getSystem().terminate();
                        System.out.println("System is shutting down...");
                    },
                    getContext().getSystem().dispatcher()
            );
        }
    }

    private void imposeHandler(Impose message, ActorRef sender) {
        int newBallot = message.getBallot();
        int v = message.getProposal();

        if((this.readBallot > newBallot || this.imposeBallot > newBallot)){
            if(DEBUG) this.log.info(this + " - sending ABORT message (" + newBallot + ") to " + sender.path().name());
            sender.tell(new Abort(newBallot), getSelf());

        }else{
            this.estimate = v;
            this.imposeBallot = newBallot;

            if(DEBUG) this.log.info(this + " - sending ACK message (" + newBallot + ") to " + sender.path().name());
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
            int proposal = -1;
            for (State s : this.states.values()){
                if (s.getEstballot() > highestEstBallot){
                    highestEstBallot = s.getEstballot();
                    proposal = s.getEst();
                }
            }

            this.states.clear();
            for (ActorRef actor : this.processes){
                actor.tell(new Impose(newBallot, proposal), getSelf());

            }
        }
    }

    private void abortHandler(Abort message, ActorRef sender) {
        return;
    }

    private void readHandler(Read r, ActorRef sender){
        int newBallot = r.getBallot();

        if((this.readBallot > newBallot || this.imposeBallot > newBallot) ){
            if(DEBUG) this.log.info(this + " - sending ABORT message (" + newBallot + ") to "+ sender.path().name());
            sender.tell(new Abort(newBallot), this.getSelf());

        }else{
            if(DEBUG) this.log.info(this + " - sending GATHER message (" + newBallot + ", " + this.imposeBallot + ", " + this.estimate+") to " + sender.path().name());
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