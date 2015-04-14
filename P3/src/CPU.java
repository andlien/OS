/**
 * Created by bflugon on 14.04.15.
 */

public class CPU implements Runnable{

    private Queue cpuQueue;
    private long maxCpuTime;
    private Process currentProcess;
    private Thread cpuThread;

    public CPU(Queue queue, long maxCpuTime){
        this.cpuQueue=queue;
        this.maxCpuTime=maxCpuTime;
        this.cpuThread=new Thread(this);
        cpuThread.start();
    }

    public void stopThread() {
        cpuThread.interrupt();
    }


    @Override
    public void run() {
        while(true){
            if(!cpuQueue.isEmpty()){
                currentProcess = (Process) cpuQueue.removeNext();
                if(currentProcess.getCpuTime()<maxCpuTime){
                    try {
                        cpuThread.sleep(currentProcess.getCpuTime());
                        currentProcess.setCpuTime(0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else {
                    try {
                        cpuThread.sleep(maxCpuTime);
                        currentProcess.setCpuTime(currentProcess.getCpuTime()-maxCpuTime);
                        cpuQueue.insert(currentProcess);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }
}

