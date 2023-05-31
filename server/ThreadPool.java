package server;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ThreadPool {

    private final List<WorkerThread> threads;
    private final List<Runnable> taskQueue;
    
    public List<Runnable> completedTasks;

    public ThreadPool(int poolSize) {
        this.threads = new LinkedList<>();
        this.taskQueue = new LinkedList<>();
        this.completedTasks = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            WorkerThread worker = new WorkerThread();
            threads.add(worker);
            worker.start();
        }
    }

    public void submit(Runnable task) {
        synchronized (taskQueue) {
            taskQueue.add(task);
            taskQueue.notify();
        }
    }

    public void shutdown() {
        synchronized (taskQueue) {
            for (WorkerThread worker : threads) {
                worker.stopThread();
            }
        }
    }

    private class WorkerThread extends Thread {
        private boolean running = true;

        public void run() {
            while (running) {

                Runnable task;
                synchronized (taskQueue) {

                    while (taskQueue.isEmpty()) {
                        try { taskQueue.wait(); } 
                        catch (InterruptedException e) { e.printStackTrace(); }
                    }

                    task = taskQueue.remove(0);
                }

                task.run();
                completedTasks.add(task);

                if (!running && taskQueue.isEmpty()) {
                    return;
                }
            }
        }

        public void stopThread() {
            running = false;
            interrupt();
        }
    }
}
