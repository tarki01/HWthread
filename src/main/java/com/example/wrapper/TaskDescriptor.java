package com.example.wrapper;

public class TaskDescriptor implements Runnable {
    Runnable task;
    private int id;
    private String description;
    public TaskDescriptor(Runnable task, int id, String description) {
        this.task = task;
        this.id = id;
        this.description = description;
    }
    public String getDescription() {
        return description;
    }
    public int getId() {
        return id;
    }

    @Override
    public void run() {
        task.run();
    }
}
