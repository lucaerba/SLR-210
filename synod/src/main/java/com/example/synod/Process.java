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
/**
 * The Process class represents an actor implementing a distributed consensus algorithm.
 * It communicates with other processes to reach an agreement on a proposed value.
 * <p>
 * Upon receiving a launch message, the process picks an input value randomly from {0, 1}
 * and invokes instances of propose operation with this value until a value is decided.
 * </p>
 */
public class Process extends UntypedAbstractActor {

    /**
     * Logger attached to the actor for logging purposes.
     */
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    /**
     * Flag indicating whether debug mode is enabled.
     */
    private final boolean DEBUG = false;

    /**
     * Number of processes in the system.
     */
    private int n;

    /**
     * ID of the current process.
     */
    private int i;

    /**
     * Constant representing the probability to fail.
     */
    private final double alpha;

    /**
     * References to other processes.
     */
    private ArrayList<ActorRef> processes;

    /**
     * Proposal value.
     */
    private int proposal;

    /**
     * Ballot number.
     */
    private int ballot;

    /**
     * Ballot number for read phase.
     */
    private int readBallot;

    /**
     * Ballot number for impose phase.
     */
    private int imposeBallot;

    /**
     * Estimated value.
     */
    private int estimate;

    /**
     * States of other processes.
     */
    private HashMap<ActorRef, State> states;

    /**
     * Number of acknowledgments received.
     */
    private int nAck = 0;

    /**
     * Flag indicating whether the process should hold.
     */
    private boolean hold;

    /**
     * Flag indicating whether the process is in fault-prone mode.
     */
    private boolean faultProneMode = false;

    /**
     * Flag indicating whether the process is in silent mode.
     */
    private boolean silentMode = false;

    /**
     * The value decided by the process. -1 if no value has been decided yet.
     */
    private int decidedValue = -1;

    /**
     * Static method to create a Props instance for the actor.
     *
     * @param n     Number of processes in the system.
     * @param i     ID of the current process.
     * @param alpha The probability to fail.
     * @return Props instance for creating the actor.
     */
    public static Props createActor(int n, int i, double alpha) {
        return Props.create(Process.class, () -> new Process(n, i, alpha));
    }

    /**
     * Constructs a Process actor with the specified parameters.
     *
     * @param n     Number of processes in the system.
     * @param i     ID of the current process.
     * @param alpha The probability to fail.
     */
    public Process(int n, int i, double alpha) {
        this.n = n;
        this.i = i;
        this.ballot = i - n;
        this.readBallot = 0;
        this.imposeBallot = i - n;
        this.estimate = -1;
        this.states = new HashMap<>();
        this.hold = false;
        this.alpha = alpha;
    }

    /**
     * Proposes a value to other processes.
     * This method is invoked upon receiving a launch message.
     *
     * @param v The value to propose.
     */
    private void propose(int v) {
        if (DEBUG) log.info(this + " - propose(" + v + ")");
        proposal = v;
        ballot += n;
        this.states.clear();

        for (ActorRef actor : this.processes) {
            if (actor.equals(getSelf())) {
                continue;
            }
            actor.tell(new Read(this.ballot), this.getSelf());
            if (DEBUG) this.log.info("Read ballot " + this.ballot + " msg: " + this + " -> " + actor.toString());
        }
    }

    /**
     * Handles incoming messages received by the actor.
     *
     * @param message The message received.
     * @throws Throwable If an error occurs during message handling.
     */
    public void onReceive(Object message) throws Throwable {
        //Silent Mode
        if (this.silentMode) {
            return;
        } else if (this.faultProneMode) {//Fault Prone Mode
            boolean crash = (((new Random()).nextDouble()) < this.alpha);
            if (crash) {
                this.silentMode = true;
                return;
            }
        }

        // Message handling logic...
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
            
        } else if (message instanceof Gather){
            if(DEBUG) log.info(this + " - gather received");
            gatherHandler((Gather) message, getSender());

        } else if (message instanceof Impose){
            if(DEBUG) log.info(this + " - impose received");
            imposeHandler((Impose) message, getSender());

        } else if (message instanceof Ack){
            if(DEBUG) log.info(this + " - ack received");
            ackHandler((Ack) message, getSender());

        } else if (message instanceof Decide){
            if(DEBUG) log.info(this + " - decide received");
            decideHandler((Decide) message, getSender());

        } else if (message instanceof Crash){
            if(DEBUG) log.info(this + " - crash received");
            this.faultProneMode = true;

        } else if(message instanceof Hold){
            if(DEBUG) log.info(this + " - hold received");
            this.hold = true;
        }
    }

    /**
     * Handles a Decide message received from another process.
     * Once a value is decided, it stops listening for messages and informs other processes.
     *
     * @param message The Decide message containing the decided value.
     * @param sender  The sender of the Decide message.
     */
    private void decideHandler(Decide message, ActorRef sender) {
        int v = message.getV();

        this.silentMode = true; // Once we decided on a value, we stop listening (for better logs)
        long time = System.currentTimeMillis() - Main.startTime;
        log.info(this + " decided received from p" + getSender() + " (value=" + v + ")" + " | time: " + time);

        for (ActorRef actor : this.processes) {
            actor.tell(new Decide(v), getSelf());
        }
    }

    /**
     * Handles an Ack message received from another process.
     * If received from a majority of processes, it informs other processes of the decided value
     * and schedules termination of the system.
     *
     * @param message The Ack message.
     * @param sender  The sender of the Ack message.
     */
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

    /**
     * Handles an Impose message received from another process.
     * It checks if the received ballot is valid and updates the estimate if necessary.
     * Then it sends an Ack or Abort message accordingly.
     *
     * @param message The Impose message containing the ballot and proposal.
     * @param sender  The sender of the Impose message.
     */
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


    /**
     * Handles a Gather message received from another process during the read phase.
     * It collects states from other processes and upon receiving a majority, selects the proposal
     * with the highest ballot and sends an Impose message to all processes.
     *
     * @param message The Gather message containing the ballot and estimate.
     * @param sender  The sender of the Gather message.
     */
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

 /**
     * Handles an Abort message received from another process.
     * It does nothing in response to an Abort message.
     *
     * @param message The Abort message.
     * @param sender  The sender of the Abort message.
     */
    private void abortHandler(Abort message, ActorRef sender) {
        return;
    }

    /**
     * Handles a Read message received from another process.
     * It checks if the received ballot is valid and sends a Gather or Abort message accordingly.
     *
     * @param r      The Read message containing the ballot.
     * @param sender The sender of the Read message.
     */
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

    
    /**
     * Represents the state of a process during the distributed consensus algorithm.
     */
    static class State {
        private Integer est;
        private int estballot;

        /**
         * Constructs a State object with the given estimate and ballot.
         *
         * @param est       The estimated value.
         * @param estballot The ballot number associated with the estimate.
         */
        public State(Integer est, int estballot) {
            this.est = est;
            this.estballot = estballot;
        }

        /**
         * Retrieves the estimated value.
         *
         * @return The estimated value.
         */
        public Integer getEst() {
            return this.est;
        }

        /**
         * Retrieves the ballot number associated with the estimate.
         *
         * @return The ballot number.
         */
        public int getEstballot() {
            return this.estballot;
        }
    }
}
