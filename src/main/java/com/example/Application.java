package com.example;

import com.example.service.concurrent.DynamicThreadPool;
import com.example.wrapper.TaskDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Application
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static void main( String[] args ) throws ExecutionException, InterruptedException {
        AtomicInteger count = new AtomicInteger(0);

        DynamicThreadPool mcts = new DynamicThreadPool(2, 4, 5, TimeUnit.SECONDS, 5, 2, DynamicThreadPool.RejectPolicy.DISCARDPOLICY);
        for (int i = 0; i < 10; i++) {
            TaskDescriptor tw = new TaskDescriptor(()-> System.out.println("Мяу"), i, "Крутое описание");
            mcts.execute(tw);
            Thread.sleep(500);
        }
        for (int i = 0; i < 1000; i++) {
            TaskDescriptor tw = new TaskDescriptor(()-> System.out.println("Мяу"), i, "Крутое описание для проверки политики отказа");
            mcts.execute(tw);
        }
        Thread.sleep(10000);
        mcts.shutdown();

    }
}
