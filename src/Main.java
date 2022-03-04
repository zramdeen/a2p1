import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
	public static void main(String[] args) throws InterruptedException {
		// setup the objects
		int N = 32;
		Labyrinth lab = new Labyrinth();
		GuestRep rep = new GuestRep(lab, N,N-1);
		Minotaur mino = new Minotaur(lab, rep, N);
		Guest guests[] = new Guest[N-1];
		for (int i = 0; i < N-1; i++) {
			guests[i] = new Guest(lab, rep, i);
		}

		// setup the threads
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

class Guest implements Runnable {
	private Labyrinth lab;
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
				if(guestRep.finished.get() == true) return;
				Thread.yield();
			}

			// visit the lab
			if(!visited && lab.getCupcake()) {
//				lab.cupcake.getAndSet(false); // eat the cupcake
				lab.eatCupcake();
				visited = true;
				System.out.println("t" + id + " visited.");
			}

			// linearization point
			lab.turn.getAndSet(lab.INVALID_TURN);

			// exit the thread
			if(guestRep.finished.get() == true) return;
		}
	}
}

class GuestRep implements Runnable {
	private Labyrinth lab;
	int id;
	private int totalGuests;
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

			// linearization point
			lab.turn.getAndSet(lab.INVALID_TURN);

			// check if all guests have visited
			if(guestVisits == totalGuests){
				finished.getAndSet(true);
				System.out.println("all threads have visited");
				return;
			}
		}
	}
}

class Minotaur implements Runnable {
	private Labyrinth lab;
	private GuestRep guestRep;
	private int totalGuests;

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
				if(guestRep.finished.get() == true) return;
				Thread.yield();
			}


			// cupcake was requested... add it
			if(lab.getRequest()) {
				lab.addCupcake();
				lab.setRequest(false);
			}

			// generate a turn
			lab.turn.getAndSet(r.nextInt(totalGuests));

			// exit the thread
			if(guestRep.finished.get() == true) return;
		}
	}
}