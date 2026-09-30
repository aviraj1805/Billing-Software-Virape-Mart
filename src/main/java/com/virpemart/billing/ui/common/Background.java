package com.virpemart.billing.ui.common;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.concurrent.Task;

/**
 * Runs slow work (reading files, imports, reports) on a background thread, so the screen never freezes.
 * The result is handed back on the screen thread. Errors go to {@link ErrorHandler}.
 */
public final class Background {

    private Background() {
    }

    /**
     * @param work      the slow work; runs on a background thread and must not touch screen controls
     * @param onSuccess receives the result on the screen thread
     * @param onDone    runs on the screen thread afterwards, whether the work succeeded or failed
     */
    public static <T> void run(String name, Callable<T> work, Consumer<T> onSuccess, Runnable onDone) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            onDone.run();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            onDone.run();
            ErrorHandler.handle(task.getException());
        });
        start(name, task);
    }

    /**
     * Like {@link #run(String, Callable, Consumer, Runnable)}, but the screen handles a failure itself,
     * for example to say "the bill is saved but was not printed" before showing the error.
     *
     * @param onFailure receives the error on the screen thread
     */
    public static <T> void run(String name, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        start(name, task);
    }

    private static void start(String name, Task<?> task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
