package com.example.synod;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.example.synod.message.Crash;
import com.example.synod.message.Hold;
import com.example.synod.message.Launch;
import com.example.synod.message.Membership;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
public class Main {

    public static long startTime;

    public static void main(String[] args) throws InterruptedException {
        if (args.length != 4) {
            for(String s:args){
                System.out.println(s);
            }
            System.out.println("Usage: java Main <N> <f> <alpha> <tle>");
            return;
        }

        int N = Integer.parseInt(args[0]);
        int f = Integer.parseInt(args[1]);
        double alpha = Double.parseDouble(args[2]);
        int tle = Integer.parseInt(args[3]);

        try {
            // Redirect System.out to a file
            File logFile = new File("system.log");
            FileOutputStream fos = new FileOutputStream(logFile, true);
            PrintStream ps = new PrintStream(fos);
            System.setOut(ps);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );
        system.log().info("System started with tle=" + tle );
        system.log().info("System started with f=" + f );
        system.log().info("System started with alpha=" + alpha );

        ArrayList<ActorRef> processes = new ArrayList<>();
        

        for (int i = 0; i < N; i++) {
            final ActorRef a = system.actorOf(Process.createActor(N, i, alpha));
            processes.add(a);
        }
        
        //give each process a view of all the other processes
        Membership m = new Membership(processes);
        for (ActorRef actor : processes) {
            actor.tell(m, ActorRef.noSender());
        }

        //Record start time
        long start = System.currentTimeMillis();
        Main.startTime = start;

        //Send LaunchMsg
        for (ActorRef actor : processes) {
            actor.tell(new Launch(), ActorRef.noSender());
        }

        //Select f random processes
        Collections.shuffle(processes);
        ArrayList<ActorRef> faultyProcesses;
        faultyProcesses = new ArrayList<>(processes.subList(0,f));

        //Send CrashMsg to f processes
        for (ActorRef actor : faultyProcesses) {
            actor.tell(new Crash(), ActorRef.noSender());
        }

        //Randomly select a not faulty process as leader
        System.out.println("The new LEADER will be: p"+processes.get(f).path().name());  //we take as leader the First non-faulty process in the shuffled array
        for (int i = 0; i < N; i++) {
            if (i!=f){
                //we stop all processes but not the leader so that it can propose alone
                system.scheduler().scheduleOnce(Duration.create(tle, TimeUnit.MILLISECONDS), processes.get(i), new Hold(), system.dispatcher(), null);
            }
        }
        //system.scheduler().scheduleOnce(Duration.create(tle, TimeUnit.MILLISECONDS), processes.get(f), new Launch(), system.dispatcher(), null);

        // Schedule the shutdown after a certain period of time (tle + tle second for safety)
        // system.scheduler().scheduleOnce(
        //         Duration.create(tle + tle, TimeUnit.MILLISECONDS),
        //         () -> {
        //             system.terminate();
        //             System.out.println("System is shutting down...");
        //         },
        //         system.dispatcher()
        // );
    }
}
