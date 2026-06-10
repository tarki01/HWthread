package com.example.service.concurrent;

import com.example.concurrent.factory.NamedThreadFactory;
import com.example.wrapper.TaskDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class DynamicThreadPool implements CustomExecutor{
    private final int corePoolSize;
    private final int maximumPoolSize;
    private final int keepAliveTime;
    private final TimeUnit unit;
    private BlockingQueue<Runnable> executorQueue;
    private List<WorkerThread> executorThreadQueue;
    private final int queueSize;
    private final int minSpareThreads;
    private final NamedThreadFactory threadFactory;
    private final RejectPolicy rejectPolicy;
    private final ReentrantLock lock = new ReentrantLock();
    private final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private boolean isWorking = true;
    public DynamicThreadPool(int corePoolSize, int maximumPoolSize, int keepAliveTime, TimeUnit unit, int queueSize, int minSpareThreads, RejectPolicy rejectPolicy) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.unit = unit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.executorQueue = new ArrayBlockingQueue<>(queueSize);
        this.executorThreadQueue = Collections.synchronizedList(new ArrayList<>());
        this.threadFactory = new NamedThreadFactory();
        this.rejectPolicy = rejectPolicy;
        createWorkThreads();
        dispenser();
    }

    private void createWorkThreads(){
        for(int i = 0; i < corePoolSize; i++){
            createWorkThread();
        }
    }

    private void createWorkThread(){
        lock.lock();
        try{
            WorkerThread wf = new WorkerThread();
            executorThreadQueue.add(wf);
            threadFactory.newThread(wf).start();
        }finally{
            lock.unlock();
        }
    }

    public void dispenser(){
        threadFactory.newThread(new Runnable() {
            private final AtomicInteger count = new AtomicInteger(0);
            @Override
            public void run() {
                while (isWorking || !executorQueue.isEmpty()){
                    if (!executorQueue.isEmpty()){
                        if (count.get() < executorThreadQueue.size()){
                            Runnable runnable = executorQueue.poll();
                            LOGGER.info("[Pool] Task accepted into queue #({}): {}", count.get(), runnable instanceof TaskDescriptor ? ((TaskDescriptor) runnable).getDescription() : "Пользователь не указал описание задачи" );
                            WorkerThread wf = executorThreadQueue.get(count.getAndIncrement());
                            wf.offer(runnable);
                            if (wf.getWorkQueue().size() > queueSize/2 && executorThreadQueue.size() < maximumPoolSize){
                                createWorkThread();
                            }
                        }else{
                            count.set(0);
                        }
                    }
                }
            }
        }).start();
    }
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new FutureTask<T>(callable);
    }
    protected void reject(Runnable runnable) throws RejectedExecutionException {
            lock.lock();
            try{
                LOGGER.info("[Rejected] Task {} was rejected due to overload!", runnable);
                switch(rejectPolicy) {
                    case ABORTPOLICY:
                        throw new RejectedExecutionException("RejectedExecutionException");
                    case DISCARDPOLICY:
                        break;
                    case CALLERRUNSPOLICY:
                        runnable.run();
                        break;
                    case DISCARDOLDERPOLICY:
                        executorQueue.poll();
                        executorQueue.offer(runnable);
                        break;
                }
            }finally{
                lock.unlock();
            }
    }
    @Override
    public void execute(Runnable command) {

        if (isWorking && !executorQueue.offer(command)){
            reject(command);
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        RunnableFuture<T> ftask = newTaskFor(task);
        execute(ftask);
        return ftask;
    }

    @Override
    public void shutdown() {
        isWorking = false;
    }

    @Override
    public void shutdownNow() {
        isWorking = false;
        executorQueue.clear();
        for (WorkerThread wf : executorThreadQueue){
            wf.getWorkQueue().clear();
            wf.getThread().interrupt();
        }
    }

    public enum RejectPolicy{
        ABORTPOLICY,
        CALLERRUNSPOLICY,
        DISCARDPOLICY,
        DISCARDOLDERPOLICY
    }
    private class WorkerThread implements Runnable{
        private BlockingQueue<Runnable> workQueue;


        public WorkerThread(){
            this.workQueue = new ArrayBlockingQueue<>(queueSize);
        }
        public boolean offer(Runnable task){
            return workQueue.offer(task);
        }

        public Thread getThread() {
            return Thread.currentThread();
        }
        public BlockingQueue<Runnable> getWorkQueue(){
            return workQueue;
        }
        @Override
        public void run() {
            while(isWorking || !workQueue.isEmpty()){
                try {
                    Runnable task = workQueue.poll(keepAliveTime, unit);
                    if(task != null){
                        if (task instanceof TaskDescriptor){
                            TaskDescriptor tw = (TaskDescriptor) task;
                            LOGGER.info("[Worker] {} executes {}", Thread.currentThread().getName(), tw.getDescription());
                        }else{
                            LOGGER.info("[Worker] {} executes {}", Thread.currentThread().getName(), "Пользователь не указал описание задачи");
                        }

                        task.run();
                    }else{
                        lock.lock();
                        try {
                            if (executorThreadQueue.size() > minSpareThreads){
                                executorThreadQueue.remove(this);
                                LOGGER.info("[Worker] {} idle timeout, stopping", Thread.currentThread().getName());
                                break;
                            }
                        }finally {
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.info("[Worker] {} terminated.",  Thread.currentThread().getName());
        }
    }
}

