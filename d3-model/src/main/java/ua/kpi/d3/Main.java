package ua.kpi.d3;

import ua.kpi.d3.experiment.ExperimentConfig;
import ua.kpi.d3.experiment.FactorialExperiment;
import ua.kpi.d3.experiment.Experiments;
import ua.kpi.d3.experiment.Output;
import ua.kpi.d3.experiment.Runner;
import ua.kpi.d3.experiment.SpeedComparison;
import ua.kpi.d3.model.ModelParams;
import ua.kpi.d3.sim.D3Simulation;
import ua.kpi.d3.sim.ProtocolPrinter;
import ua.kpi.d3.sim.SimulationResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length > 0 && !args[0].startsWith("--") ? args[0] : "all";
        List<String> opts = new ArrayList<>(List.of(args));
        if (!opts.isEmpty() && opts.get(0).equals(command)) {
            opts.remove(0);
        }
        String lib = value(opts, "--lib", LibraryVariant.MODIFIED);
        if (!lib.equals(LibraryVariant.ORIGINAL) && !lib.equals(LibraryVariant.MODIFIED)) {
            System.out.println("Невідома бібліотека: " + lib);
            return;
        }
        if (!lib.equals(LibraryVariant.loaded())) {
            if (Boolean.getBoolean("d3.relaunched")) {
                throw new IllegalStateException("Не вдалося завантажити бібліотеку " + lib);
            }
            System.exit(LibraryVariant.relaunch(lib, List.of(args)));
        }
        if (command.equals("bench")) {
            SpeedComparison.benchChild(opts.contains("--quick") ? ExperimentConfig.quick() : ExperimentConfig.full());
            return;
        }
        if (command.equals("run")) {
            runSingle(opts);
            return;
        }

        ExperimentConfig cfg = opts.contains("--quick") ? ExperimentConfig.quick() : ExperimentConfig.full();
        cfg.warmup = Double.parseDouble(value(opts, "--warmup", String.valueOf(cfg.warmup)));
        cfg.observation = Double.parseDouble(value(opts, "--T", String.valueOf(cfg.observation)));

        Path dir = Path.of(value(opts, "--out", cfg.quick ? "results-quick" : "results"));
        int threads = Integer.parseInt(value(opts, "--threads",
                String.valueOf(Math.max(1, Runtime.getRuntime().availableProcessors() - 1))));

        try (Output out = new Output(dir, command + "_log.txt"); Runner runner = new Runner(threads)) {
            out.println("Бібліотека: " + LibraryVariant.describeLoaded() + ".");
            Experiments ex = new Experiments(cfg, out, runner);
            switch (command) {
                case "protocol" -> ex.protocol();
                case "verify" -> ex.verification();
                case "transient" -> ex.transientPeriod();
                case "baseline" -> ex.baseline(ex.replications());
                case "normality" -> ex.estimatesDistribution();
                case "intervals" -> ex.callIntervals();
                case "steady" -> ex.bufferSteady();
                case "lossfree" -> ex.bufferLossFree();
                case "proposals" -> ex.proposals();
                case "duplex" -> ex.duplexSatellite();
                case "speed" -> new SpeedComparison(cfg, out).run();
                case "factorial" -> new FactorialExperiment(cfg, out, runner).run();
                case "all" -> {
                    long t0 = System.currentTimeMillis();
                    ex.runAll();
                    new FactorialExperiment(cfg, out, runner).run();
                    new SpeedComparison(cfg, out).run();
                    out.printf("%nЧас виконання: %.1f с, потоків: %d.%n", (System.currentTimeMillis() - t0) / 1000.0,
                            runner.threads());
                }
                default -> System.out.println("Невідома команда: " + command);
            }
        }
    }

    private static void runSingle(List<String> o) {
        ModelParams p = ModelParams.base()
                .withBufferSize(Integer.parseInt(value(o, "--N", "2")))
                .withArrival(Double.parseDouble(value(o, "--arrival", "10")), Double.parseDouble(second(o, "--arrival", "3")))
                .withChannelTime(Double.parseDouble(value(o, "--channel", "10")))
                .withSatellite(Double.parseDouble(value(o, "--sat", "10")), Double.parseDouble(second(o, "--sat", "5")));
        double t = Double.parseDouble(value(o, "--T", "300000"));
        double w = Double.parseDouble(value(o, "--warmup", "10000"));
        D3Simulation s = new D3Simulation(p);
        if (o.contains("--protocol")) {
            s.addObserver(new ProtocolPrinter(s.net(), System.out));
        }
        SimulationResult r = s.run(t, w);
        System.out.println();
        System.out.print(r.summary());
    }

    private static String value(List<String> o, String key, String def) {
        int i = o.indexOf(key);
        return i >= 0 && i + 1 < o.size() ? o.get(i + 1) : def;
    }

    private static String second(List<String> o, String key, String def) {
        int i = o.indexOf(key);
        return i >= 0 && i + 2 < o.size() && !o.get(i + 2).startsWith("--") ? o.get(i + 2) : def;
    }
}
