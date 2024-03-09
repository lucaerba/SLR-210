package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.example.synod.message.Crash;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;

import java.util.*;

public class Main {
    public static int N = 3;//process number
    public static int tle = 100; //waiting time
    public static int f=1; //Select f processes
    public static long startTime;

    public static void main(String[] args) throws InterruptedException {
        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );
        system.log().info("System started with tle=" + tle );

        ArrayList<ActorRef> processes = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            final ActorRef a = system.actorOf(Process.createActor(N, i));
            processes.add(a);
        }

        //Record start time
        long start = System.currentTimeMillis();
        Main.startTime = start;

        //give each process a view of all the other processes
        Membership m = new Membership(processes);
        for (ActorRef actor : processes) {
            actor.tell(m, ActorRef.noSender());
        }

        //Send LaunchMsg
        for (ActorRef actor : processes) {
            actor.tell(new Launch(), ActorRef.noSender());
        }

        //Select f random processes
        ArrayList<ActorRef> faultyProcesses;
        faultyProcesses = new ArrayList<>(processes);
        Collections.shuffle(faultyProcesses);
        faultyProcesses = new ArrayList<>(faultyProcesses.subList(0,f));

        //Send CrashMsg to f processes
        for (ActorRef actor : faultyProcesses) {
            actor.tell(new Crash(), ActorRef.noSender());
        }
    }
}
