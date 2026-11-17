package ua.kpi.d3.experiment;

import ua.kpi.d3.model.ModelParams;
import ua.kpi.d3.sim.D3Simulation;
import ua.kpi.d3.sim.SimulationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Runner implements AutoCloseable {
    private final ExecutorService pool;
    private final int threads;

    public Runner() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    public Runner(int threads) {
        this.threads = threads;
        this.pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "d3-sim");
            t.setDaemon(true);
            return t;
        });
    }

    public int threads() {
        return threads;
    }

    public List<SimulationResult> replicate(ModelParams params, int p, double simulationTime, double warmup) {
        List<Future<SimulationResult>> futures = new ArrayList<>();
        for (int i = 0; i < p; i++) {
            futures.add(pool.submit(() -> D3Simulation.run(params, simulationTime, warmup)));
        }
        return collect(futures);
    }

    public <T> List<T> runAll(List<java.util.concurrent.Callable<T>> tasks) {
        List<Future<T>> futures = new ArrayList<>();
        for (java.util.concurrent.Callable<T> task : tasks) {
            futures.add(pool.submit(task));
        }
        return collect(futures);
    }

    private static <T> List<T> collect(List<Future<T>> futures) {
        List<T> results = new ArrayList<>();
        try {
            for (Future<T> f : futures) {
                results.add(f.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Помилка під час імітації: " + e.getCause(), e.getCause());
        }
        return results;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
