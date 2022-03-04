/*=============================================================================
| Assignment: Problem 1: Simulate the Minotaur's Labyrinth.
|
| Author: Zahid Ramdeen
| Language: Java
|
| To Compile: (from terminal)
| javac Main.java
|
| To Execute: (from terminal) Note: needs at least 2 threads.
| java Main <number of threads>
|
| Class: COP4520 - Concepts of Parallel and Distributed Processing - Spring 2022
| Instructor: Damian Dechev
| Due Date: 3/4/2022
|
+=============================================================================*/

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
	public static void main(String[] args) throws Exception {
		// obtain command line argument from user.
		if(args.length == 0) {
			System.out.println("enter the number to stop at as an argument (eg: java A1 100)");
			System.exit(0);
		}

		// ensure the user entered a number that is at least 2 or more.
		final int TOTAL_THREADS = Integer.parseInt(args[0]);
		if(TOTAL_THREADS < 2)
			throw new Exception("Needs at least 2 threads");

		// set up the objects
		int N = TOTAL_THREADS;
		Labyrinth lab = new Labyrinth();
		GuestRep rep = new GuestRep(lab, N,N-1);
		Minotaur mino = new Minotaur(lab, rep, N);
		Guest guests[] = new Guest[N-1];
		for (int i = 0; i < N-1; i++) {
			guests[i] = new Guest(lab, rep, i);
		}

		// set up and start the threads
		Thread tmino = new Thread(mino, "mino");
		tmino.start();
		for (int i = 0; i < N-1; i++) {
			Thread guest = new Thread(guests[i], "t"+i);
			guest.start();
		}
		Thread trep = new Thread(rep, "t" + (N-1));
		trep.start();
	}
}

/**
 * Shared object. Used to synchronize the threads.
 */
class Labyrinth {
	AtomicInteger turn;
	private boolean request;
	private boolean cupcake;
	final int INVALID_TURN = -1;

	Labyrinth(){
		turn = new AtomicInteger(INVALID_TURN);
		cupcake = true;
		request = false;
	}

	public void eatCupcake(){ cupcake = false; }
	public void addCupcake(){ cupcake = true; }
	public boolean getCupcake() { return cupcake; }

	public void setRequest(boolean req){ request = req; }
	public boolean getRequest(){ return request; }
}

/**
 * A Guest invited to the Minotaur's Party.
 * Each guest will enter the party an indefinite amount of time.
 * A guest can only eat a cupcake once.
 * After they have eaten a cupcake they must leave the labyrinth all subsequent times.
 */
class Guest implements Runnable {
	private final Labyrinth lab;
	private static GuestRep guestRep;
	int id;
	private boolean visited;

	Guest(Labyrinth lab, GuestRep rep, int id){
		this.lab = lab;
		guestRep = rep;
		this.id = id;
		visited = false;
	}

	@Override
	public void run() {
		while(true){
			// wait till it's cur thread's turn
			while(lab.turn.get() != id){
				// rep says its done. exit
				if(guestRep.finished.get()) return;
				Thread.yield();
			}

			// visit the lab
			if(!visited && lab.getCupcake()) {
				lab.eatCupcake();
				visited = true;
//				System.out.println("t" + id + " visited."); // enable to view visits
			}

			// linearization point
			lab.turn.getAndSet(lab.INVALID_TURN);

			// exit the game b/c Rep signaled the end
			if(guestRep.finished.get()) return;
		}
	}
}

/**
 * A Guest invited to the Minotaur's Party.
 * Responsible for counting how many threads have visited the labyrinth.
 * Signals the end of the game once all guests have visited the lab.
 */
class GuestRep implements Runnable {
	private final Labyrinth lab;
	int id;
	private final int totalGuests;
	private int guestVisits;
	AtomicBoolean finished;

	GuestRep(Labyrinth lab, int totalGuests, int id){
		this.lab = lab;
		this.totalGuests = totalGuests;
		this.id = id;
		guestVisits = 1;
		finished = new AtomicBoolean(false);
	}

	@Override
	public void run() {
		while(true){
			// wait till it's cur thread's turn
			while(lab.turn.get() != id){ Thread.yield(); }

			// check the cupcake in the lab
			if(!lab.getCupcake()){
				guestVisits++;
				lab.setRequest(true);
			}

			// linearization point... this lets Mino generate a new turn
			lab.turn.getAndSet(lab.INVALID_TURN);

			// check if all guests have visited... if yes signal to end the game
			if(guestVisits == totalGuests){
				finished.getAndSet(true);
				System.out.println("all threads have visited");
				return;
			}
		}
	}
}

/**
 * Owns the Labyrinth.
 * Can add cupcakes to the Labyrinth upon request.
 * Stops the game when GuestRep signals the end.
 */
class Minotaur implements Runnable {
	private final Labyrinth lab;
	private final GuestRep guestRep;
	private final int totalGuests;

	Minotaur(Labyrinth lab, GuestRep rep, int totalGuests){
		this.lab = lab;
		this.guestRep = rep;
		this.totalGuests = totalGuests;
	}

	@Override
	public void run() {
		Random r = new Random();
		while(true){
			// wait until there is no1 in the lab
			while(lab.turn.get() != lab.INVALID_TURN){
				// to avoid deadlock. exit if rep says it's done
				if(guestRep.finished.get()) return;
				Thread.yield(); // don't spin if there is no work to do.
			}

			// cupcake was requested... add it
			if(lab.getRequest()) {
				lab.addCupcake();
				lab.setRequest(false);
			}

			// generate a turn
			lab.turn.getAndSet(r.nextInt(totalGuests));

			// exit the game if Rep requests it.
			if(guestRep.finished.get()) return;
		}
	}
}