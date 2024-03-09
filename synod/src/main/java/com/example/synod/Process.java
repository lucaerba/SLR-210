package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.example.synod.Main.LaunchMsg;
import com.example.synod.Main.CrashMsg;
//import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.util.Random;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private boolean faultProneMode = false;
    private boolean silentMode = false;

    private int n;//number of processes
    private int i;//id of current process
    private Membership processes;//other processes' references
    private Integer proposal;
    private int ballot;
    private final double alpha = 0.3;

    /*
     * Static method to create an actor
     */
    public static Props createActor(int n, int i) {
        return Props.create(Process.class, () -> new Process(n, i));
    }

    public Process(int n, int i) {
        this.n = n;
        this.i = i;
        this.ballot = i-n;
    }

    private void propose(Integer v) {
        log.info(this + " - propose("+ v+")");
        this.proposal = v;
        this.ballot += n;
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
            processes = m;
        } else if (message instanceof LaunchMsg) {
            log.info(this + " - launch received");
            int v = (new Random()).nextInt(2);
            this.propose(v);

        } else if (message instanceof CrashMsg){
            this.log.info("p" + self().path().name()+" received LAUNCH.");
            this.faultProneMode = true;
        }
    }
    
    @Override
    public String toString() {
        return "Process #" + i;
    }

}
