package com.example.concurrent.factory;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadFactory implements ThreadFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String PREFIX = "worker-thread-";
    private final AtomicInteger count = new AtomicInteger(1);
    @Override
    public Thread newThread(Runnable r) {
        int threadNum = count.getAndIncrement();
        Thread worker = new Thread(r, PREFIX + threadNum);
        LOGGER.warn(String.format("[ThreadFactory] Creating new thread: %s", worker.getName()));
        worker.setUncaughtExceptionHandler((thread, throwable) -> LOGGER.error("Thread {} {}", thread, throwable));

        return worker;
    }

}
