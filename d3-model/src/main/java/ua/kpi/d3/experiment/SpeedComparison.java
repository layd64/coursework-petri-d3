package ua.kpi.d3.experiment;

import ua.kpi.d3.LibraryVariant;
import ua.kpi.d3.model.ModelParams;
import ua.kpi.d3.sim.D3Simulation;
import ua.kpi.d3.sim.InvariantChecker;
import ua.kpi.d3.sim.SimulationResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpeedComparison {
    private final ExperimentConfig cfg;
    private final Output out;

    public SpeedComparison(ExperimentConfig cfg, Output out) {
        this.cfg = cfg;
        this.out = out;
    }

    private record Bench(String variant, double horizon, int run, double ms, long events, double f, double u) {
    }

    public void run() {
        out.section(String.format("10. Швидкодія бібліотеки: оригінал і модифікація. Прогонів = %d.", cfg.speedReps));

        List<Bench> all = new ArrayList<>();
        for (String variant : new String[]{LibraryVariant.ORIGINAL, LibraryVariant.MODIFIED}) {
            out.println();
            out.println("Бібліотека: " + variant);
            List<String> args = new ArrayList<>(List.of("bench", "--lib", variant));
            if (cfg.quick) {
                args.add("--quick");
            }
            Process p;
            try {
                ProcessBuilder pb = new ProcessBuilder(LibraryVariant.javaCommand(variant, args));
                pb.redirectErrorStream(true);
                p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        String[] f = line.split(";");
                        if (f[0].equals("BENCH")) {
                            Bench b = new Bench(variant, Double.parseDouble(f[1]), Integer.parseInt(f[2]),
                                    Double.parseDouble(f[3]), Long.parseLong(f[4]), Double.parseDouble(f[5]),
                                    Double.parseDouble(f[6]));
                            all.add(b);
                            out.printf("T = %8.0f мс, прогін %d: %8.1f мс, подій = %d%n", b.horizon, b.run, b.ms, b.events);
                        } else if (f[0].equals("INV")) {
                            out.println("Інваріанти: " + f[1]);
                        } else if (f[0].equals("LIB")) {
                            continue;
                        } else if (!line.isBlank()) {
                            out.println(line);
                        }
                    }
                }
                if (p.waitFor() != 0) {
                    out.println("помилка, код " + p.exitValue());
                }
            } catch (Exception e) {
                throw new IllegalStateException("Не вдалося виконати порівняння швидкодії", e);
            }
        }

        out.println();
        out.printf("%10s | %10s %7s %10s %11s | %10s %7s %10s %11s | %9s | %9s %9s | %9s %9s%n",
                "T, мс", "Ориг., мс", "СКВ", "медіана", "подій/с", "Модиф., мс", "СКВ", "медіана", "подій/с",
                "Прискор.", "f ориг.", "f модиф.", "U ориг.", "U модиф.");
        List<String> rows = new ArrayList<>();
        double lastSpeedup = 0;
        for (double h : cfg.speedHorizons) {
            double[] to = times(all, LibraryVariant.ORIGINAL, h);
            double[] tm = times(all, LibraryVariant.MODIFIED, h);
            double eo = rate(all, LibraryVariant.ORIGINAL, h);
            double em = rate(all, LibraryVariant.MODIFIED, h);
            double fo = avg(all, LibraryVariant.ORIGINAL, h, true);
            double fm = avg(all, LibraryVariant.MODIFIED, h, true);
            double uo = avg(all, LibraryVariant.ORIGINAL, h, false);
            double um = avg(all, LibraryVariant.MODIFIED, h, false);
            double speedup = Stats.quantile(to, 0.5) / Stats.quantile(tm, 0.5);
            lastSpeedup = speedup;
            out.printf("%10.0f | %10.1f %7.1f %10.1f %11.0f | %10.1f %7.1f %10.1f %11.0f | %8.2fx | %9.4f %9.4f | %9.5f %9.5f%n",
                    h, Stats.mean(to), Stats.std(to), Stats.quantile(to, 0.5), eo, Stats.mean(tm), Stats.std(tm),
                    Stats.quantile(tm, 0.5), em, speedup, fo, fm, uo, um);
            rows.add(Output.row(h, Stats.mean(to), Stats.std(to), Stats.quantile(to, 0.5), eo, Stats.mean(tm),
                    Stats.std(tm), Stats.quantile(tm, 0.5), em, speedup, fo, fm, uo, um));
        }
        out.csv("speed_comparison.csv", "Horizon_ms;Original_ms;Original_std;Original_median_ms;Original_events_per_s;"
                + "Modified_ms;Modified_std;Modified_median_ms;Modified_events_per_s;Speedup;SatCallFreq_original;SatCallFreq_modified;"
                + "SatUtil_original;SatUtil_modified", rows);
        List<String> runRows = new ArrayList<>();
        for (Bench b : all) {
            runRows.add(Output.row(b.variant, b.horizon, b.run, b.ms, b.events, b.f, b.u));
        }
        out.csv("speed_runs.csv", "Library;Horizon_ms;Run;Time_ms;Events;SatCallFreq;SatUtil", runRows);

        double hMax = cfg.speedHorizons[cfg.speedHorizons.length - 1];
        double[] fo = values(all, LibraryVariant.ORIGINAL, hMax, true);
        double[] fm = values(all, LibraryVariant.MODIFIED, hMax, true);
        double diff = Math.abs(Stats.mean(fo) - Stats.mean(fm));
        double se = Math.sqrt(Stats.std(fo) * Stats.std(fo) / fo.length + Stats.std(fm) * Stats.std(fm) / fm.length);
        out.println();
        out.printf("f при T = %.0f мс: оригінал = %.4f, модифікація = %.4f, різниця = %.4f, 2*se = %.4f%n",
                hMax, Stats.mean(fo), Stats.mean(fm), diff, 2 * se);
        out.conclusion(String.format(java.util.Locale.US, "модифікована бібліотека %s; різниця f %s.",
                lastSpeedup > 1 ? String.format(java.util.Locale.US, "швидша в %.2f раза", lastSpeedup) : "не швидша", diff <= 2 * se ? "у межах похибки" : "більша за 2*se (замало прогонів або зміна поведінки)"));
    }

    private static double[] times(List<Bench> all, String v, double h) {
        return all.stream().filter(b -> b.variant.equals(v) && b.horizon == h).mapToDouble(Bench::ms).toArray();
    }

    private static double[] values(List<Bench> all, String v, double h, boolean freq) {
        return all.stream().filter(b -> b.variant.equals(v) && b.horizon == h)
                .mapToDouble(b -> freq ? b.f : b.u).toArray();
    }

    private static double avg(List<Bench> all, String v, double h, boolean freq) {
        return Stats.mean(values(all, v, h, freq));
    }

    private static double rate(List<Bench> all, String v, double h) {
        double ev = all.stream().filter(b -> b.variant.equals(v) && b.horizon == h).mapToLong(Bench::events).sum();
        double ms = all.stream().filter(b -> b.variant.equals(v) && b.horizon == h).mapToDouble(Bench::ms).sum();
        return ev / ms * 1000.0;
    }

    public static void benchChild(ExperimentConfig cfg) {
        System.out.println("LIB;бібліотека: " + LibraryVariant.describeLoaded());
        ModelParams base = ModelParams.base();

        for (int i = 0; i < cfg.speedWarmupRuns; i++) {
            D3Simulation.run(base, cfg.speedWarmupTime, 0);
        }

        D3Simulation s = new D3Simulation(base);
        InvariantChecker inv = new InvariantChecker(s.net());
        s.addObserver(inv);
        s.run(600_000, 0);
        System.out.printf(Locale.US, "INV;перевірок = %d, порушень: %s%n", inv.checks(), inv.ok() ? "немає" : "є");

        for (double h : cfg.speedHorizons) {
            for (int r = 1; r <= cfg.speedReps; r++) {
                long t0 = System.nanoTime();
                SimulationResult res = D3Simulation.run(base, h, 10_000);
                double ms = (System.nanoTime() - t0) / 1e6;
                System.out.printf(Locale.US, "BENCH;%.0f;%d;%.3f;%d;%.6f;%.8f%n", h, r, ms, res.events,
                        res.satCallFrequency(), res.satUtilization);
            }
        }
    }
}
