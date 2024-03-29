import java.io.*;

/**
 * The main class of the P3 exercise. This class is only partially complete.
 */
public class Simulator implements Constants
{
	/** The queue of events to come */
    private EventQueue eventQueue;
	/** Reference to the memory unit */
    private Memory memory;
	/** Reference to the GUI interface */
	private Gui gui;
	/** Reference to the statistics collector */
	public static Statistics statistics;
	/** The global clock */
    private long clock;
	/** The length of the simulation */
	private long simulationLength;
	/** The average length between process arrivals */
	private long avgArrivalInterval;

	private CPU cpu;
	private IO io;
    private long maxCpuTime;
	private long avgIOTime;

	/**
	 * Constructs a scheduling simulator with the given parameters.
	 * @param memoryQueue			The memory queue to be used.
	 * @param cpuQueue				The CPU queue to be used.
	 * @param ioQueue				The I/O queue to be used.
	 * @param memorySize			The size of the memory.
	 * @param maxCpuTime			The maximum time quant used by the RR algorithm.
	 * @param avgIoTime				The average length of an I/O operation.
	 * @param simulationLength		The length of the simulation.
	 * @param avgArrivalInterval	The average time between process arrivals.
	 * @param gui					Reference to the GUI interface.
	 */
	public Simulator(Queue memoryQueue, Queue cpuQueue, Queue ioQueue, long memorySize,
			long maxCpuTime, long avgIoTime, long simulationLength, long avgArrivalInterval, Gui gui) {
		this.simulationLength = simulationLength;
		this.avgArrivalInterval = avgArrivalInterval;
		this.gui = gui;
		statistics = new Statistics();
		eventQueue = new EventQueue();
		memory = new Memory(memoryQueue, memorySize, statistics);
		clock = 0;
        cpu = new CPU(cpuQueue, maxCpuTime);
        io = new IO(ioQueue);
		this.avgIOTime = avgIoTime;
        this.maxCpuTime=maxCpuTime;
    }

    /**
	 * Starts the simulation. Contains the main loop, processing events.
	 * This method is called when the "Start simulation" button in the
	 * GUI is clicked.
	 */
	public void simulate() {
		System.out.print("Simulating...");
		// Genererate the first process arrival event
		eventQueue.insertEvent(new Event(NEW_PROCESS, 0));
		// Process events until the simulation length is exceeded:
		while (clock < simulationLength && !eventQueue.isEmpty()) {
			// Find the next event
			Event event = eventQueue.getNextEvent();
			// Find out how much time that passed...
			long timeDifference = event.getTime()-clock;
			// ...and update the clock.
			clock = event.getTime();
			// Let the memory unit and the GUI know that time has passed
			memory.timePassed(timeDifference);
			gui.timePassed(timeDifference);
			// Deal with the event
			if (clock < simulationLength) {
				processEvent(event);
			}

			// Note that the processing of most events should lead to new
			// events being added to the event queue!

		}
		System.out.println("..done.");

		// End the simulation by printing out the required statistics
		statistics.printReport(simulationLength);
	}

	/**
	 * Processes an event by inspecting its type and delegating
	 * the work to the appropriate method.
	 * @param event	The event to be processed.
	 */
	private void processEvent(Event event) {
        switch (event.getType()) {
			case NEW_PROCESS:
				createProcess();
				break;
			case SWITCH_PROCESS:
				switchProcess();
				break;
			case END_PROCESS:
				endProcess();
				break;
			case IO_REQUEST:
				processIoRequest();
				break;
			case END_IO:
				endIoOperation();
				break;
		}
	}

	/**
	 * Simulates a process arrival/creation.
	 */
	private void createProcess() {
		// Create a new process
		Process newProcess = new Process(memory.getMemorySize(), clock);
		memory.insertProcess(newProcess);
		flushMemoryQueue();
		// Add an event for the next process arrival
		long nextArrivalTime = clock + 1 + (long)(2*Math.random()*avgArrivalInterval);
		eventQueue.insertEvent(new Event(NEW_PROCESS, nextArrivalTime));
		// Update statistics
		statistics.nofCreatedProcesses++;
    }

	/**
	 * Transfers processes from the memory queue to the ready queue as long as there is enough
	 * memory for the processes.
	 */
	private void flushMemoryQueue() {
		Process p = memory.checkMemory(clock);
		// As long as there is enough memory, processes are moved from the memory queue to the cpu queue
		while(p != null) {

			// Also add new events to the event queue if needed
            p.setTimeToNextIoOperation();
            this.cpu.getQueue().insert(p);
            p.enteredReadyQueue(clock);

            if (this.cpu.getCurrentProcess() == null) {
				this.cpu.processNext();
                this.cpu.getCurrentProcess().leftReadyQueue(clock);
                this.cpu.getCurrentProcess().enteredCpu(clock);
                this.eventQueue.insertEvent(createEvent(this.cpu.getCurrentProcess()));
                this.gui.setCpuActive(this.cpu.getCurrentProcess());
            }

			// Check for more free memory
			p = memory.checkMemory(clock);
		}
	}

	/**
	 * Simulates a process switch.
	 */
	private void switchProcess() {
        Process p = cpu.getCurrentProcess();
        p.leftCpu(clock);

		statistics.totCpuQueue += cpu.getQueue().getQueueLength();
		statistics.numAddedCpuQueue++;

        p.enteredReadyQueue(clock);
		//p.setCpuTime(p.getCpuTime() - (clock - p.getTimeOfLastEvent()));
		cpu.getQueue().insert(p);
		cpu.processNext();
        p = this.cpu.getCurrentProcess();
        //p.setTimeOfLastEvent(clock);
        p.leftReadyQueue(clock);
        p.enteredCpu(clock);

        this.eventQueue.insertEvent(createEvent(p));
        this.gui.setCpuActive(p);

		statistics.processSwitches++;
	}

	/**
	 * Ends the active process, and deallocates any resources allocated to it.
	 */
	private void endProcess() {
        Process p = cpu.getCurrentProcess();
		p.leftCpu(clock);

		statistics.totCpuQueue += cpu.getQueue().getQueueLength();
		statistics.numAddedCpuQueue++;

		memory.processCompleted(p);
		p.updateStatistics(statistics);
		flushMemoryQueue();
        if (this.cpu.getQueue().isEmpty()) {
            this.cpu.setCurrentProcess(null);
            this.gui.setCpuActive(null);
        }
        else {
            //TODO: Fix this shit
            cpu.processNext();
            cpu.getCurrentProcess().leftReadyQueue(clock);
			cpu.getCurrentProcess().enteredCpu(clock);
			//p.setTimeOfLastEvent(clock);
            //cpu.setCurrentProcess((Process)cpu.getQueue().removeNext());
            this.eventQueue.insertEvent(createEvent(cpu.getCurrentProcess()));
            this.gui.setCpuActive(cpu.getCurrentProcess());
        }

		//statistics.totalCpuTime += p.getTimeSpentInCpu();
        statistics.totalTimeSpentWaitingForIO += p.getTimeSpentWaitingForIo();
        statistics.totalTimeSpentWaitingForCpu += p.getTimeSpentInReadyQueue();
        statistics.totalIoTime += p.getTimeSpentInIo();
        //statistics.totalTimeSpentWaitingForMemory += p.getTimeSpentWaitingForMemory();
	}

	/**
	 * Processes an event signifying that the active process needs to
	 * perform an I/O operation.
	 */
	private void processIoRequest() {
        //TODO: Fix processing IO queue
        Process  p = cpu.getCurrentProcess();
        p.leftCpu(clock);

		statistics.totCpuQueue += cpu.getQueue().getQueueLength();
		statistics.numAddedCpuQueue++;

		if (io.getCurrentProcess() == null) {
			io.setCurrentProcess(p);
			gui.setIoActive(p);
			eventQueue.insertEvent(new Event(END_IO, this.clock + (long) (avgIOTime * 2 * Math.random())));
            p.enteredIo(clock);
		} else {
			io.getQueue().insert(p);
            p.enteredIoQueue(clock);
			if (io.getQueue().getQueueLength() > statistics.largestIoQueue)
				statistics.largestIoQueue = io.getQueue().getQueueLength();
		}

		//cpu.processNext();
		//gui.setCpuActive(cpu.getCurrentProcess());
        if (this.cpu.getQueue().isEmpty()) {
            this.cpu.setCurrentProcess(null);
            this.gui.setCpuActive(null);
        }
        else {
            this.cpu.processNext();
            this.cpu.getCurrentProcess().enteredCpu(clock);
            this.eventQueue.insertEvent(createEvent(this.cpu.getCurrentProcess()));
            this.gui.setCpuActive(this.cpu.getCurrentProcess());
            this.cpu.getCurrentProcess().leftReadyQueue(clock);
        }

	}

	/**
	 * Processes an event signifying that the process currently doing I/O
	 * is done with its I/O operation.
	 */
	private void endIoOperation() {
		Process p = io.getCurrentProcess();
		p.setTimeToNextIoOperation();
        p.leftI0(clock);

		statistics.totIoQueue += io.getQueue().getQueueLength();
		statistics.numAddedIoQueue++;

        p.enteredReadyQueue(clock);
		cpu.getQueue().insert(p);
		if (cpu.getQueue().getQueueLength() > statistics.largestCpuQueue)
			statistics.largestCpuQueue = cpu.getQueue().getQueueLength();

        if (this.io.getQueue().isEmpty()) {
            this.io.setCurrentProcess(null);
            this.gui.setIoActive(null);
        }
        else {
            this.io.processNext();
            this.io.getCurrentProcess().leftIoQueue(clock);
			this.gui.setIoActive(this.io.getCurrentProcess());
			eventQueue.insertEvent(new Event(END_IO, this.clock + (long) (avgIOTime*2*Math.random())));
			this.io.getCurrentProcess().enteredIo(clock);
        }
        if (this.cpu.getCurrentProcess() == null) {
            this.cpu.processNext();
            this.cpu.getCurrentProcess().enteredCpu(clock);
            eventQueue.insertEvent(createEvent(this.cpu.getCurrentProcess()));
            this.gui.setCpuActive(this.cpu.getCurrentProcess());
        }

		statistics.processIO++;
	}

    private Event createEvent(Process process) {
        Event event;
        if (this.maxCpuTime >= process.getTimeToNextIoOperation()) {
            if (process.getCpuTime() > process.getTimeToNextIoOperation()) {
                event = new Event(IO_REQUEST, this.clock + process.getTimeToNextIoOperation());
            }
            else {
                event = new Event(END_PROCESS, this.clock + process.getCpuTime());
            }
        }
        else {
            if (process.getCpuTime() > this.maxCpuTime) {
                event = new Event(SWITCH_PROCESS, this.clock + this.maxCpuTime);
            }
            else {
                event = new Event(END_PROCESS, this.clock + process.getCpuTime());
            }
        }
        return event;
    }

	/**
	 * Reads a number from the an input reader.
	 * @param reader	The input reader from which to read a number.
	 * @return			The number that was inputted.
	 */
	public static long readLong(BufferedReader reader) {
		try {
			return Long.parseLong(reader.readLine());
		} catch (IOException ioe) {
			return 100;
		} catch (NumberFormatException nfe) {
			return 0;
		}
	}

	/**
	 * The startup method. Reads relevant parameters from the standard input,
	 * and starts up the GUI. The GUI will then start the simulation when
	 * the user clicks the "Start simulation" button.
	 * @param args	Parameters from the command line, they are ignored.
	 */
	public static void main(String args[]) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Please input system parameters: ");

		System.out.print("Memory size (KB): ");
		long memorySize = readLong(reader);
		while(memorySize < 400) {
			System.out.println("Memory size must be at least 400 KB. Specify memory size (KB): ");
			memorySize = readLong(reader);
		}

		System.out.print("Maximum uninterrupted cpu time for a process (ms): ");
		long maxCpuTime = readLong(reader);

		System.out.print("Average I/O operation time (ms): ");
		long avgIoTime = readLong(reader);

		System.out.print("Simulation length (ms): ");
		long simulationLength = readLong(reader);
		while(simulationLength < 1) {
			System.out.println("Simulation length must be at least 1 ms. Specify simulation length (ms): ");
			simulationLength = readLong(reader);
		}

		System.out.print("Average time between process arrivals (ms): ");
		long avgArrivalInterval = readLong(reader);

		SimulationGui gui = new SimulationGui(memorySize, maxCpuTime, avgIoTime, simulationLength, avgArrivalInterval);
	}
}
