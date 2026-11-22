package ua.kpi.d3.experiment;

import ua.kpi.d3.model.D3Net;
import ua.kpi.d3.model.ModelParams;
import ua.kpi.d3.sim.D3Simulation;
import ua.kpi.d3.sim.InvariantChecker;
import ua.kpi.d3.sim.ProtocolPrinter;
import ua.kpi.d3.sim.SimulationResult;
import ua.kpi.d3.sim.TimeSeriesRecorder;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.ToDoubleFunction;

public final class Experiments {
    private final ExperimentConfig cfg;
    private final Output out;
    private final Runner runner;

    public Experiments(ExperimentConfig cfg, Output out, Runner runner) {
        this.cfg = cfg;
        this.out = out;
        this.runner = runner;
    }

    public void protocol() {
        out.section("1. Протокол подій.");
        protocolRun("protocol_base.txt", ModelParams.base(), 100,
                "Базові параметри. T = 100 мс.");
        protocolRun("protocol_buffer1.txt", ModelParams.base().withBufferSize(1), 150,
                "Обсяг буфера N = 1. T = 150 мс.");

        PrintStream original = System.out;
        try (PrintStream f = out.openFile("protocol_library.txt")) {
            System.setOut(f);
            D3Simulation s = new D3Simulation(ModelParams.base()).withLibraryProtocol(true);
            System.out.println("Протокол бібліотеки PetriObjLib. T = 40 мс.");
            System.out.println("Позиції: " + placeNames(s));
            s.run(40, 0);
        } finally {
            System.setOut(original);
        }
        out.println("Збережено: protocol_library.txt");
    }

    private void protocolRun(String file, ModelParams p, double time, String title) {
        try (PrintStream f = out.openFile(file)) {
            D3Simulation s = new D3Simulation(p);
            PrintStream both = new PrintStream(new TeeOutputStream(System.out, f), true,
                    java.nio.charset.StandardCharsets.UTF_8);
            both.println();
            both.println(title);
            both.println("Параметри: " + p);
            s.addObserver(new ProtocolPrinter(s.net(), both));
            SimulationResult r = s.run(time, 0);
            both.println();
            both.print(r.summary());
            both.flush();
        }
        out.println("Збережено: " + file);
    }

    private static String placeNames(D3Simulation s) {
        StringBuilder sb = new StringBuilder();
        for (var pl : s.net().net.getListP()) {
            sb.append(pl.getName()).append(' ');
        }
        return sb.toString();
    }

    public void verification() {
        out.section("2. Верифікація моделі. T = 3 600 000 мс. Перехідний період = 10 000 мс.");
        ModelParams b = ModelParams.base();
        Object[][] cases = {
                {"Базові значення", b,
                        "переповнення є, але рідкі (rho = 1)"},
                {"Інтервал надходжень 20±3 мс", b.withArrival(20, 3),
                        "rho = 0.5; черги немає: f = 0, Uсуп = 0, Uкан = 0.5"},
                {"Інтервал надходжень 10±0 мс (детерм.)", b.withArrival(10, 0),
                        "пакет надходить у момент завершення передачі: f = 0, Uкан = 1"},
                {"Час передачі каналом 5 мс", b.withChannelTime(5),
                        "rho = 0.5; f = 0, Uсуп = 0, Uкан = 0.5"},
                {"Час передачі каналом 15 мс", b.withChannelTime(15),
                        "Uкан = 1; переповнення 2*(100-66.7) = 66.7 1/с"},
                {"Час передачі каналом 20 мс", b.withChannelTime(20),
                        "Uкан = 1; переповнення 2*(100-50) = 100 1/с, багато втрат"},
                {"Обсяг буфера N = 1", b.withBufferSize(1),
                        "аналітично: Pпереп = 1/3, f = 200/3 = 66.7 1/с, Uкан = 2/3"},
                {"Обсяг буфера N = 10", b.withBufferSize(10),
                        "f та втрати суттєво зменшуються"},
                {"Супутник 10±0 мс (детерм.)", b.withSatellite(10, 0),
                        "Uсуп = fобсл * 10 мс"},
                {"Супутник 1±0 мс", b.withSatellite(1, 0),
                        "втрати різко зменшуються, Uсуп у 10 разів менше"},
                {"Супутник 30±0 мс", b.withSatellite(30, 0),
                        "втрати та Uсуп зростають"},
        };
        double t = 3_600_000;
        double w = 10_000;
        List<Callable<Object[]>> tasks = new ArrayList<>();
        for (Object[] c : cases) {
            ModelParams p = (ModelParams) c[1];
            tasks.add(() -> {
                D3Simulation s = new D3Simulation(p);
                InvariantChecker inv = new InvariantChecker(s.net());
                s.addObserver(inv);
                return new Object[]{s.run(t, w), inv};
            });
        }
        List<Object[]> res = runner.runAll(tasks);

        out.printf("%-3s %-38s %5s %9s %9s %8s %9s %9s %9s %7s %7s %6s %6s %7s %10s%n",
                "№", "Змінені вхідні змінні", "rho", "f, 1/с", "fобсл,1/с", "Uсуп", "fобсл*S", "Pвтр", "Pпереп",
                "UканA", "UканB", "БуфA", "maxБуф", "Баланс", "Інваріанти");
        List<String> rows = new ArrayList<>();
        long totalChecks = 0;
        long totalPriority = 0;
        boolean allOk = true;
        for (int i = 0; i < cases.length; i++) {
            SimulationResult r = (SimulationResult) res.get(i)[0];
            InvariantChecker inv = (InvariantChecker) res.get(i)[1];
            totalChecks += inv.checks();
            totalPriority += inv.priorityChecks();
            allOk &= inv.ok() && r.balanceHolds() && r.maxBufferOccupancyBoth() <= r.params.bufferSize;
            ModelParams p = r.params;
            double check = r.satServedFrequency() * p.satMean / 1000.0;
            out.printf("%-3d %-38s %5.2f %9.4f %9.4f %8.5f %9.5f %9.2e %9.2e %7.4f %7.4f %6.3f %6d %7s %10s%n",
                    i + 1, cases[i][0], p.channelLoad(), r.satCallFrequency(), r.satServedFrequency(),
                    r.satUtilization, check, r.lossProbability(), r.overflowProbability(),
                    r.channelUtilization[0], r.channelUtilization[1], r.meanBufferOccupancy[0],
                    r.maxBufferOccupancyBoth(), r.balanceHolds() ? "так" : "ні",
                    inv.ok() ? "так" : "ні");
            for (String v : inv.violations()) {
                out.println("      ! " + v);
            }
            rows.add(Output.row(i + 1, cases[i][0], p.arrivalMean, p.arrivalDev, p.channelTime, p.bufferSize,
                    p.satMean, p.satDev, p.channelLoad(), r.satCallFrequency(), r.satServedFrequency(),
                    r.satUtilization, check, r.lossProbability(), r.overflowProbability(), r.lossFrequency(),
                    r.channelUtilization[0], r.channelUtilization[1], r.meanBufferOccupancy[0],
                    r.meanBufferOccupancy[1], r.maxBufferOccupancyBoth(), r.balanceHolds(), inv.ok(),
                    inv.checks(), cases[i][2]));
        }
        out.csv("verification.csv", "No;Case;ArrivalMean_ms;ArrivalDev_ms;ChannelTime_ms;BufferSize;SatMean_ms;SatDev_ms;"
                + "Rho;SatCallFreq_per_s;SatServedFreq_per_s;SatUtilization;ServedFreqTimesMeanS;LossProb;OverflowProb;"
                + "LossFreq_per_s;ChannelUtilA;ChannelUtilB;MeanBufferA;MeanBufferB;MaxBuffer;BalanceOk;InvariantsOk;"
                + "InvariantChecks;Expected", rows);
        out.println();
        out.printf("Перевірка інваріантів: перевірок = %d, з них пріоритетів = %d, порушень: %s%n",
                totalChecks, totalPriority, allOk ? "немає" : "є");
        out.conclusion(allOk ? "інваріанти і баланс пакетів виконуються, модель працює коректно."
                : "є порушення, модель треба перевірити.");

        verifyDelays();
        verifyConflictResolution();

        SimulationResult r = D3Simulation.run(ModelParams.base(), 600_000, 0);
        out.println();
        out.println("Порівняння зі статистикою бібліотеки. T = 600 000 мс.");
        out.printf("%-10s %12s %12s%n", "", "програма", "бібліотека");
        out.printf("%-10s %12.6f %12.6f%n", "Uсуп", r.satUtilization, 1 - r.libSatFreeMean);
        out.printf("%-10s %12.6f %12.6f%n", "UканA", r.channelUtilization[0], r.libChannelBusyMean[0]);
        out.printf("%-10s %12.6f %12.6f%n", "ЧергаA", r.meanQueue[0], r.libQueueMean[0]);
        boolean same = Math.abs(r.satUtilization - (1 - r.libSatFreeMean)) < 1e-9
                && Math.abs(r.channelUtilization[0] - r.libChannelBusyMean[0]) < 1e-9
                && Math.abs(r.meanQueue[0] - r.libQueueMean[0]) < 1e-9;
        out.conclusion(same ? "статистика програми збігається зі статистикою бібліотеки."
                : "статистика програми відрізняється від статистики бібліотеки.");
    }

    private boolean delaysOk;

    private void verifyDelays() {
        D3Simulation s = new D3Simulation(ModelParams.base());
        var net = s.net();
        net.a.arrival.setMoments(true);
        net.a.transmit.setMoments(true);
        net.satellites[0].transmit.setMoments(true);
        s.run(600_000, 0);

        out.println();
        out.println("Затримки переходів. T = 600 000 мс.");
        out.printf("%-14s %8s %9s %9s %9s %9s %9s %9s %9s %12s%n", "Перехід", "n", "сер.", "теор.",
                "СКВ", "теор.", "мін", "макс", "хі2", "хі2кр");
        List<String> rows = new ArrayList<>();
        delaysOk = true;
        rows.addAll(delayRow("A.Arrival", durations(net.a.arrival), 10, 3, 12));
        rows.addAll(delayRow("A.Transmit", durations(net.a.transmit), 10, 0, 0));
        rows.addAll(delayRow("Sat.Transmit", durations(net.satellites[0].transmit), 10, 5, 20));
        out.csv("verification_delays.csv", "Transition;BinLeft_ms;BinRight_ms;Observed;Expected", rows);
        out.conclusion(delaysOk ? "затримки переходів відповідають заданим законам розподілу."
                : "є затримки, що не відповідають заданим законам.");
    }

    private void verifyConflictResolution() {
        ModelParams p = ModelParams.base().withArrival(10, 0).withChannelTime(15).withSatellite(10, 0);
        SimulationResult r = D3Simulation.run(p, 600_000, 0);
        int n = r.satCalls[0] + r.satCalls[1];
        double share = (double) r.satCalls[0] / n;
        double se = Math.sqrt(0.25 / n);
        out.println();
        out.println("Конфлікт A.ToSatellite / B.ToSatellite. T = 600 000 мс.");
        out.printf("Конфліктів = %d, A = %d (%.4f), B = %d (%.4f), |частка - 0.5| = %.4f, 2*se = %.4f%n",
                n, r.satCalls[0], share, r.satCalls[1], 1 - share, Math.abs(share - 0.5), 2 * se);
        out.conclusion(Math.abs(share - 0.5) <= 2 * se ? "конфлікт розв'язується рівноймовірно."
                : "частки відрізняються від 0.5 більше за похибку.");
    }

    private static double[] durations(com.stetsenko.petriobjlib.PetriT tr) {
        List<Double> in = tr.getInMoments();
        List<Double> outM = tr.getOutMoments();
        int k = Math.min(in.size(), outM.size());
        double[] d = new double[k];
        for (int i = 0; i < k; i++) {
            d[i] = outM.get(i) - in.get(i);
        }
        return d;
    }

    private List<String> delayRow(String name, double[] d, double mean, double dev, int bins) {
        double sdTheory = dev / Math.sqrt(3);
        List<String> rows = new ArrayList<>();
        String chi = "-";
        String chiCr = "-";
        if (bins > 0) {
            int[] obs = new int[bins];
            double lo = mean - dev;
            double w = 2 * dev / bins;
            for (double v : d) {
                int b = (int) Math.floor((v - lo) / w);
                obs[Math.max(0, Math.min(bins - 1, b))]++;
            }
            double expected = (double) d.length / bins;
            double x2 = 0;
            for (int b = 0; b < bins; b++) {
                x2 += (obs[b] - expected) * (obs[b] - expected) / expected;
                rows.add(Output.row(name, lo + b * w, lo + (b + 1) * w, obs[b], expected));
            }
            double cr = Stats.chiSquareQuantile(0.95, bins - 1);
            chi = String.format(java.util.Locale.US, "%.2f", x2);
            chiCr = String.format(java.util.Locale.US, "%.2f", cr);
            delaysOk &= x2 < cr;
        }
        out.printf("%-14s %8d %9.4f %9.4f %9.4f %9.4f %9.4f %9.4f %9s %12s%n", name, d.length, Stats.mean(d), mean,
                Stats.std(d), sdTheory, Stats.min(d), Stats.max(d), chi, chiCr);
        return rows;
    }

    public void transientPeriod() {
        out.section(String.format("3. Перехідний період. Прогонів = %d, T = %.0f мс.",
                cfg.transientRuns, cfg.transientHorizon));
        List<Callable<List<TimeSeriesRecorder.Point>>> tasks = new ArrayList<>();
        for (int i = 0; i < cfg.transientRuns; i++) {
            tasks.add(() -> {
                D3Simulation s = new D3Simulation(ModelParams.base());
                TimeSeriesRecorder rec = new TimeSeriesRecorder(s.net(), cfg.transientStep);
                s.addObserver(rec);
                s.run(cfg.transientHorizon, 0);
                return rec.points();
            });
        }
        List<List<TimeSeriesRecorder.Point>> series = runner.runAll(tasks);
        int n = series.stream().mapToInt(List::size).min().orElse(0);
        int runs = series.size();

        Map<String, ToDoubleFunction<TimeSeriesRecorder.Point>> responses = new LinkedHashMap<>();
        responses.put("Uсуп", pt -> pt.satUtilization);
        responses.put("f", pt -> pt.satCallFrequency);
        responses.put("Буфер", pt -> pt.meanBufferOccupancy);

        double tMod = 0;
        double tWarm = 0;
        int k = 0;
        out.printf("%-8s %12s %12s %14s%n", "", "стале знач.", "eps", "T стабіл., мс");
        for (Map.Entry<String, ToDoubleFunction<TimeSeriesRecorder.Point>> e : responses.entrySet()) {
            double[] spread = new double[n];
            double finalMean = 0;
            for (int c = 0; c < n; c++) {
                double mn = Double.MAX_VALUE;
                double mx = -Double.MAX_VALUE;
                for (List<TimeSeriesRecorder.Point> s : series) {
                    double v = e.getValue().applyAsDouble(s.get(c));
                    mn = Math.min(mn, v);
                    mx = Math.max(mx, v);
                }
                spread[c] = mx - mn;
            }
            for (List<TimeSeriesRecorder.Point> s : series) {
                finalMean += e.getValue().applyAsDouble(s.get(n - 1)) / runs;
            }
            double eps = cfg.transientRelEps * finalMean;
            int idx = n;
            for (int c = n - 1; c >= 0 && spread[c] < eps; c--) {
                idx = c;
            }
            double tStar = idx < n ? series.get(0).get(idx).time : Double.NaN;
            out.printf("%-8s %12.5f %12.5f %14s%n",
                    e.getKey(), finalMean, eps, Double.isNaN(tStar) ? "-" : String.format("%.0f", tStar));
            if (k < 2) {
                if (Double.isNaN(tStar)) {
                    tStar = cfg.transientHorizon;
                }
                tMod = Math.max(tMod, tStar);
            }
            if (k == 2 && !Double.isNaN(tStar)) {
                tWarm = Math.max(tStar, warmupFromOccupancy(series, finalMean, eps, n));
            }
            k++;
        }

        List<String> rows = new ArrayList<>();
        StringBuilder header = new StringBuilder("time_s");
        for (String key : new String[]{"U", "F", "Occ"}) {
            for (int i = 1; i <= runs; i++) {
                header.append(';').append(key).append(i);
            }
        }
        for (int c = 0; c < n; c++) {
            StringBuilder sb = new StringBuilder(String.format(java.util.Locale.US, "%.1f",
                    series.get(0).get(c).time / 1000.0));
            for (List<TimeSeriesRecorder.Point> s : series) {
                sb.append(';').append(String.format(java.util.Locale.US, "%.6g", s.get(c).satUtilization));
            }
            for (List<TimeSeriesRecorder.Point> s : series) {
                sb.append(';').append(String.format(java.util.Locale.US, "%.6g", s.get(c).satCallFrequency));
            }
            for (List<TimeSeriesRecorder.Point> s : series) {
                sb.append(';').append(String.format(java.util.Locale.US, "%.6g", s.get(c).meanBufferOccupancy));
            }
            rows.add(sb.toString());
        }
        out.csv("transient.csv", header.toString(), rows);

        if (tMod > 0) {
            cfg.observation = roundUp(tMod, 50_000);
        }
        cfg.warmup = Math.max(5_000, roundUp(tWarm, 5_000));
        out.println();
        out.conclusion(String.format(java.util.Locale.US,
                "перші %.0f мс відкидаються, далі спостереження %.0f мс.", cfg.warmup, cfg.observation));
    }

    private static double warmupFromOccupancy(List<List<TimeSeriesRecorder.Point>> series, double steady,
                                              double eps, int n) {
        double t = 0;
        for (List<TimeSeriesRecorder.Point> s : series) {
            int idx = n;
            for (int c = n - 1; c >= 0 && Math.abs(s.get(c).meanBufferOccupancy - steady) < eps; c--) {
                idx = c;
            }
            if (idx < n) {
                t = Math.max(t, s.get(idx).time);
            }
        }
        return t;
    }

    private static double roundUp(double v, double step) {
        return Math.ceil(v / step) * step;
    }

    public int replications() {
        double total = cfg.warmup + cfg.observation;
        out.section(String.format("4. Кількість прогонів. Пробних прогонів = %d, Tп = %.0f мс, Tмод = %.0f мс.",
                cfg.pilotRuns, cfg.warmup, cfg.observation));
        out.printf("%-6s %10s %10s %10s %6s%n", "", "середнє", "sigma", "eps", "p");
        List<SimulationResult> pilot = runner.replicate(ModelParams.base(), cfg.pilotRuns, total, cfg.warmup);
        double[] f = pilot.stream().mapToDouble(SimulationResult::satCallFrequency).toArray();
        double[] u = pilot.stream().mapToDouble(r -> r.satUtilization).toArray();
        int pf = repsFor("f", f);
        int pu = repsFor("Uсуп", u);
        int p = Math.max(cfg.minReplications, Math.max(pf, pu));
        out.conclusion(String.format(java.util.Locale.US, "для точності %.0f%% з ймовірністю %.2f потрібно p = %d прогонів.",
                cfg.relEps * 100, cfg.beta, p));
        if (p > cfg.maxReplications) {
            out.printf("quick: p обмежено до %d%n", cfg.maxReplications);
            p = cfg.maxReplications;
        }
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < pilot.size(); i++) {
            rows.add(Output.row(i + 1, f[i], u[i]));
        }
        out.csv("replications_pilot.csv", "Run;SatCallFreq_per_s;SatUtilization", rows);
        return p;
    }

    private int repsFor(String name, double[] x) {
        double m = Stats.mean(x);
        double s = Stats.std(x);
        double eps = cfg.relEps * m;
        int p = Stats.chebyshevReplications(s, eps, cfg.beta);
        out.printf("%-6s %10.5f %10.5f %10.5f %6d%n", name, m, s, eps, p);
        return p;
    }

    public void baseline(int p) {
        double total = cfg.warmup + cfg.observation;
        out.section(String.format("5. Базові параметри. p = %d, Tп = %.0f мс, Tмод = %.0f мс.",
                p, cfg.warmup, cfg.observation));
        List<SimulationResult> res = runner.replicate(ModelParams.base(), p, total, cfg.warmup);

        Map<String, ToDoubleFunction<SimulationResult>> m = new LinkedHashMap<>();
        m.put("f, 1/с", SimulationResult::satCallFrequency);
        m.put("fобсл, 1/с", SimulationResult::satServedFrequency);
        m.put("Uсуп", r -> r.satUtilization);
        m.put("fвтр, 1/с", SimulationResult::lossFrequency);
        m.put("Pвтр", SimulationResult::lossProbability);
        m.put("Pпереп", SimulationResult::overflowProbability);
        m.put("Pвідм", SimulationResult::satRefusalProbability);
        m.put("UканA", r -> r.channelUtilization[0]);
        m.put("UканB", r -> r.channelUtilization[1]);
        m.put("БуфA", r -> r.meanBufferOccupancy[0]);
        m.put("БуфB", r -> r.meanBufferOccupancy[1]);

        out.printf("%-12s %12s %12s %12s %12s %12s%n", "", "середнє", "СКВ", "мін", "макс", "eps");
        List<String> rows = new ArrayList<>();
        Map<String, double[]> est = new LinkedHashMap<>();
        for (Map.Entry<String, ToDoubleFunction<SimulationResult>> e : m.entrySet()) {
            double[] x = res.stream().mapToDouble(e.getValue()).toArray();
            double mean = Stats.mean(x);
            double sd = Stats.std(x);
            double eps = Stats.chebyshevHalfWidth(sd, p, cfg.beta);
            est.put(e.getKey(), new double[]{mean, eps});
            out.printf("%-12s %12.6f %12.6f %12.6f %12.6f %12.6f%n", e.getKey(), mean, sd, Stats.min(x), Stats.max(x), eps);
            rows.add(Output.row(e.getKey(), mean, sd, Stats.min(x), Stats.max(x), eps));
        }
        out.csv("baseline_summary.csv", "Characteristic;Mean;Std;Min;Max;Eps", rows);
        out.conclusion(String.format(java.util.Locale.US, "f = %.3f ± %.3f 1/с, Uсуп = %.4f ± %.4f.",
                est.get("f, 1/с")[0], est.get("f, 1/с")[1], est.get("Uсуп")[0], est.get("Uсуп")[1]));

        List<String> runRows = new ArrayList<>();
        for (int i = 0; i < res.size(); i++) {
            SimulationResult r = res.get(i);
            runRows.add(Output.row(i + 1, r.satCallFrequency(), r.satServedFrequency(), r.satUtilization,
                    r.lossProbability(), r.overflowProbability(), r.satCallAttempts(), r.lostTotal(),
                    r.totalArrivedObserved()));
        }
        out.csv("baseline_runs.csv", "Run;SatCallFreq_per_s;SatServedFreq_per_s;SatUtilization;LossProb;OverflowProb;"
                + "SatCalls;Lost;Arrived", runRows);
    }

    public void estimatesDistribution() {
        int n = cfg.normalityRuns;
        int k = cfg.normalityBins;
        out.section(String.format("5б. Закон розподілу оцінок f і Uсуп. Прогонів = %d, Tп = %.0f мс, Tмод = %.0f мс.",
                n, cfg.warmup, cfg.observation));
        List<SimulationResult> res = runner.replicate(ModelParams.base(), n, cfg.warmup + cfg.observation, cfg.warmup);
        double[][] data = {
                res.stream().mapToDouble(SimulationResult::satCallFrequency).toArray(),
                res.stream().mapToDouble(r -> r.satUtilization).toArray()};
        String[] names = {"f", "Uсуп"};
        String[] keys = {"SatCallFreq", "SatUtilization"};
        double crit = Stats.chiSquareQuantile(0.95, k - 1 - 2);

        out.printf("Хі-квадрат для нормального закону, %d рівноймовірних інтервалів, alpha = 0.05:%n", k);
        out.printf("%-6s %12s %12s %12s %12s %10s %10s %12s%n", "", "середнє", "СКВ", "мін", "макс", "хі2", "хі2кр",
                "приймається");
        List<String> summary = new ArrayList<>();
        List<String> hist = new ArrayList<>();
        boolean all = true;
        for (int j = 0; j < data.length; j++) {
            double[] x = data[j];
            double mean = Stats.mean(x);
            double sd = Stats.std(x);
            java.util.function.DoubleUnaryOperator cdf = v -> Stats.normalCdf((v - mean) / sd);
            double chi = chiSquareEquiprobable(x, cdf, k);
            boolean ok = chi < crit;
            all &= ok;
            out.printf("%-6s %12.6f %12.6f %12.6f %12.6f %10.2f %10.2f %12s%n", names[j], mean, sd, Stats.min(x),
                    Stats.max(x), chi, crit, ok ? "так" : "ні");
            summary.add(Output.row(keys[j], mean, sd, Stats.min(x), Stats.max(x), chi, crit, k - 3));

            int bins = 8;
            double lo = Stats.min(x);
            double w = (Stats.max(x) - lo) / bins;
            for (int b = 0; b < bins; b++) {
                double a = lo + b * w;
                double c = a + w;
                final int last = b == bins - 1 ? 1 : 0;
                long obs = java.util.Arrays.stream(x).filter(v -> v >= a && (v < c || last == 1)).count();
                double exp = x.length * (cdf.applyAsDouble(c) - cdf.applyAsDouble(a));
                hist.add(Output.row(keys[j], a, c, obs, exp));
            }
        }
        out.csv("estimates_normality.csv", "Characteristic;Mean;Std;Min;Max;Chi2;Chi2Crit;Df", summary);
        out.csv("estimates_hist.csv", "Characteristic;BinLeft;BinRight;Observed;ExpectedNormal", hist);
        out.conclusion(all ? "гіпотеза про нормальний закон розподілу оцінок f і Uсуп приймається."
                : "гіпотеза про нормальний закон розподілу приймається не для всіх оцінок.");
    }

    public void callIntervals() {
        double horizon = cfg.callIntervalHorizon;
        out.section(String.format("5а. Інтервал між викликами супутника. T = %.0f мс, Tп = %.0f мс.",
                horizon, cfg.warmup));
        D3Simulation s = new D3Simulation(ModelParams.base());
        var net = s.net();
        com.stetsenko.petriobjlib.PetriT[] calls = {net.a.toSatellite, net.b.toSatellite, net.a.loss, net.b.loss};
        for (var t : calls) {
            t.setMoments(true);
        }
        s.run(cfg.warmup + horizon, cfg.warmup);
        List<Double> moments = new ArrayList<>();
        for (var t : calls) {
            for (double m : t.getInMoments()) {
                if (m >= cfg.warmup) {
                    moments.add(m);
                }
            }
        }
        java.util.Collections.sort(moments);
        double[] d = new double[moments.size() - 1];
        for (int i = 1; i < moments.size(); i++) {
            d[i - 1] = moments.get(i) - moments.get(i - 1);
        }
        double mean = Stats.mean(d);
        double sd = Stats.std(d);
        double cv = sd / mean;
        out.printf("n = %d, середнє = %.2f мс, СКВ = %.2f мс, CV = %.3f, мін = %.3f мс, макс = %.1f мс, медіана = %.1f мс%n",
                d.length, mean, sd, cv, Stats.min(d), Stats.max(d), Stats.quantile(d, 0.5));

        int k = 20;

        java.util.function.DoubleUnaryOperator expCdf = x -> 1 - Math.exp(-x / mean);
        double chiExp = chiSquareEquiprobable(d, expCdf, k);
        double crExp = Stats.chiSquareQuantile(0.95, k - 1 - 1);

        double shape = (mean / sd) * (mean / sd);
        double scale = sd * sd / mean;
        java.util.function.DoubleUnaryOperator gammaCdf = x -> Stats.regularizedGammaP(shape, x / scale);
        double chiGamma = chiSquareEquiprobable(d, gammaCdf, k);
        double crGamma = Stats.chiSquareQuantile(0.95, k - 1 - 2);

        double[] logs = java.util.Arrays.stream(d).map(Math::log).toArray();
        double muLn = Stats.mean(logs);
        double sigmaLn = Stats.std(logs);
        java.util.function.DoubleUnaryOperator lnCdf = x -> x <= 0 ? 0 : Stats.normalCdf((Math.log(x) - muLn) / sigmaLn);
        double chiLn = chiSquareEquiprobable(d, lnCdf, k);
        double crLn = Stats.chiSquareQuantile(0.95, k - 1 - 2);
        out.printf("Хі-квадрат, %d інтервалів, alpha = 0.05:%n", k);
        out.printf("%-38s %10s %10s %10s%n", "закон", "хі2", "хі2кр", "приймається");
        out.printf("%-38s %10.1f %10.1f %10s%n", String.format(java.util.Locale.US, "експоненціальний (%.1f мс)", mean),
                chiExp, crExp, chiExp < crExp ? "так" : "ні");
        out.printf("%-38s %10.1f %10.1f %10s%n", String.format(java.util.Locale.US, "гамма (a = %.3f, b = %.1f мс)", shape, scale),
                chiGamma, crGamma, chiGamma < crGamma ? "так" : "ні");
        out.printf("%-38s %10.1f %10.1f %10s%n", String.format(java.util.Locale.US, "логнормальний (mu = %.3f, s = %.3f)", muLn, sigmaLn),
                chiLn, crLn, chiLn < crLn ? "так" : "ні");
        String[] laws = {"експоненціальний", "гамма", "логнормальний"};
        double[] chi = {chiExp, chiGamma, chiLn};
        double[] cr = {crExp, crGamma, crLn};
        int best = 0;
        for (int i = 1; i < chi.length; i++) {
            if (chi[i] < chi[best]) {
                best = i;
            }
        }
        if (chi[best] < cr[best]) {
            out.conclusion("закон розподілу приймається: " + laws[best] + ".");
        } else {
            out.conclusion("жоден закон не приймається, ближчий - " + laws[best] + ".");
        }

        double w = 25;
        int bins = 40;
        List<String> rows = new ArrayList<>();
        for (int b = 0; b < bins; b++) {
            double lo = b * w;
            double hi = lo + w;
            long obs = java.util.Arrays.stream(d).filter(v -> v >= lo && v < hi).count();
            double eExp = d.length * (expCdf.applyAsDouble(hi) - expCdf.applyAsDouble(lo));
            double eGam = d.length * (gammaCdf.applyAsDouble(hi) - gammaCdf.applyAsDouble(lo));
            double eLn = d.length * (lnCdf.applyAsDouble(hi) - lnCdf.applyAsDouble(lo));
            rows.add(Output.row(lo, hi, obs, eExp, eGam, eLn));
        }
        out.csv("call_intervals_hist.csv", "BinLeft_ms;BinRight_ms;Observed;ExpectedExponential;ExpectedGamma;ExpectedLognormal",
                rows);
        out.csv("call_intervals_summary.csv", "n;Mean_ms;Std_ms;CV;Min_ms;Max_ms;ChiExp;ChiExpCrit;GammaShape;GammaScale_ms;"
                + "ChiGamma;ChiGammaCrit;LnMu;LnSigma;ChiLn;ChiLnCrit", List.of(Output.row(d.length, mean, sd, cv,
                Stats.min(d), Stats.max(d), chiExp, crExp, shape, scale, chiGamma, crGamma, muLn, sigmaLn, chiLn, crLn)));
    }

    private static double chiSquareEquiprobable(double[] d, java.util.function.DoubleUnaryOperator cdf, int k) {
        int[] obs = new int[k];
        for (double v : d) {
            int b = (int) Math.floor(cdf.applyAsDouble(v) * k);
            obs[Math.min(k - 1, Math.max(0, b))]++;
        }
        double e = (double) d.length / k;
        double x2 = 0;
        for (int o : obs) {
            x2 += (o - e) * (o - e) / e;
        }
        return x2;
    }

    public void bufferSteady() {
        out.section(String.format("6. Вплив обсягу буфера. Tп = %.0f мс, Tмод = %.0f мс, p = %d.",
                cfg.steadyWarmup, cfg.steadyObservation, cfg.steadyReps));
        int[] ns = cfg.steadyBufferSizes;
        Map<Integer, List<SimulationResult>> byN = new LinkedHashMap<>();
        for (int n : ns) {
            byN.put(n, runner.replicate(ModelParams.base().withBufferSize(n), cfg.steadyReps,
                    cfg.steadyWarmup + cfg.steadyObservation, cfg.steadyWarmup));
        }
        out.printf("%4s %12s %10s %10s %10s %12s %12s %12s %10s %10s%n", "N", "f, 1/с", "eps(f)", "Uсуп", "eps(U)",
                "Pвтр", "Втрат/год", "Pпереп", "Буф.сер", "Час, мс");
        List<String> rows = new ArrayList<>();
        double[] fMean = new double[ns.length];
        double[] pLoss = new double[ns.length];
        double[] uMean = new double[ns.length];
        for (int i = 0; i < ns.length; i++) {
            List<SimulationResult> rs = byN.get(ns[i]);
            double[] f = rs.stream().mapToDouble(SimulationResult::satCallFrequency).toArray();
            double[] u = rs.stream().mapToDouble(r -> r.satUtilization).toArray();
            double[] pl = rs.stream().mapToDouble(SimulationResult::lossProbability).toArray();
            double[] lf = rs.stream().mapToDouble(r -> r.lossFrequency() * 3600).toArray();
            double[] po = rs.stream().mapToDouble(SimulationResult::overflowProbability).toArray();
            double[] occ = rs.stream().mapToDouble(r -> (r.meanBufferOccupancy[0] + r.meanBufferOccupancy[1]) / 2).toArray();
            double[] tau = rs.stream().mapToDouble(SimulationResult::meanTimeInBuffer).toArray();
            fMean[i] = Stats.mean(f);
            uMean[i] = Stats.mean(u);
            pLoss[i] = Stats.mean(pl);
            double ef = Stats.chebyshevHalfWidth(Stats.std(f), rs.size(), cfg.beta);
            double eu = Stats.chebyshevHalfWidth(Stats.std(u), rs.size(), cfg.beta);
            out.printf("%4d %12.5f %10.5f %10.6f %10.6f %12.3e %12.3f %12.3e %10.4f %10.3f%n", ns[i], fMean[i], ef,
                    uMean[i], eu, pLoss[i], Stats.mean(lf), Stats.mean(po), Stats.mean(occ), Stats.mean(tau));
            rows.add(Output.row(ns[i], fMean[i], Stats.std(f), ef, uMean[i], Stats.std(u), eu, pLoss[i], Stats.std(pl),
                    Stats.mean(lf), Stats.mean(po), Stats.mean(occ), Stats.mean(tau)));
        }
        out.csv("buffer_steady.csv", "BufferSize;SatCallFreq_mean;SatCallFreq_std;SatCallFreq_eps;SatUtil_mean;SatUtil_std;"
                + "SatUtil_eps;LossProb_mean;LossProb_std;LossesPerHour;OverflowProb;MeanBufferOccupancy;MeanTimeInBuffer_ms", rows);

        out.println();
        out.println("Регресія y = 1 / (b0 + b1 * N), N >= 2:");
        regressionHyperbolic("f", ns, fMean);
        regressionHyperbolic("Uсуп", ns, uMean);
        out.println("Регресія y = e^b0 * N^b1, N >= 2:");
        regressionLogLog("f", ns, fMean);
        regressionLogLog("Pвтр", ns, pLoss);
        List<String> fitRows = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) {
            fitRows.add(Output.row(ns[i], fMean[i], uMean[i], pLoss[i]));
        }
        out.csv("buffer_steady_means.csv", "BufferSize;SatCallFreq;SatUtil;LossProb", fitRows);

        out.println();
        int[] lv = cfg.anovaLevels;
        double[][] yF = new double[lv.length][];
        double[][] yU = new double[lv.length][];
        for (int j = 0; j < lv.length; j++) {
            List<SimulationResult> rs = byN.get(lv[j]);
            if (rs == null) {
                rs = runner.replicate(ModelParams.base().withBufferSize(lv[j]), cfg.steadyReps,
                        cfg.steadyWarmup + cfg.steadyObservation, cfg.steadyWarmup);
            }
            yF[j] = rs.stream().mapToDouble(SimulationResult::satCallFrequency).toArray();
            yU[j] = rs.stream().mapToDouble(r -> r.satUtilization).toArray();
        }
        StringBuilder levels = new StringBuilder();
        for (int v : lv) {
            levels.append(v).append(' ');
        }
        out.println("Дисперсійний аналіз, фактор N = " + levels.toString().trim() + ", alpha = 0.05:");
        boolean sf = anovaPrint("f", Stats.anova(yF, 1 - cfg.beta));
        boolean su = anovaPrint("Uсуп", Stats.anova(yU, 1 - cfg.beta));
        boolean down = fMean[fMean.length - 1] < fMean[0] && uMean[uMean.length - 1] < uMean[0];
        out.conclusion("зі збільшенням N f і Uсуп " + (down ? "зменшуються" : "не зменшуються")
                + ", вплив N " + (sf && su ? "значущий." : "значущий не для всіх відгуків."));
    }

    private void regressionHyperbolic(String name, int[] ns, double[] y) {
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> yOrig = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) {
            if (ns[i] >= 2 && y[i] > 0) {
                xs.add(new double[]{ns[i]});
                ys.add(1.0 / y[i]);
                yOrig.add(y[i]);
            }
        }
        if (ys.size() < 3) {
            out.println(name + ": мало значень");
            return;
        }
        Stats.RegressionResult r = Stats.regression(xs.toArray(new double[0][]),
                ys.stream().mapToDouble(Double::doubleValue).toArray());

        double[] yo = yOrig.stream().mapToDouble(Double::doubleValue).toArray();
        double ym = Stats.mean(yo);
        double ssRes = 0;
        double ssTot = 0;
        for (int i = 0; i < yo.length; i++) {
            double fit = 1.0 / r.fitted[i];
            ssRes += (yo[i] - fit) * (yo[i] - fit);
            ssTot += (yo[i] - ym) * (yo[i] - ym);
        }
        out.printf("%-6s b0 = %9.5f, b1 = %9.5f, R(1/y) = %.4f, R(y) = %.4f%n",
                name, r.b[0], r.b[1], r.correlationIndex, Math.sqrt(Math.max(0, 1 - ssRes / ssTot)));
    }

    private void regressionLogLog(String name, int[] ns, double[] y) {
        List<double[]> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (int i = 0; i < ns.length; i++) {
            if (ns[i] >= 2 && y[i] > 0) {
                xs.add(new double[]{Math.log(ns[i])});
                ys.add(Math.log(y[i]));
            }
        }
        if (ys.size() < 3) {
            out.println(name + ": мало значень");
            return;
        }
        Stats.RegressionResult r = Stats.regression(xs.toArray(new double[0][]),
                ys.stream().mapToDouble(Double::doubleValue).toArray());
        out.printf("%-6s b0 = %8.4f, b1 = %8.4f, y = %.5g * N^(%.4f), R = %.4f%n",
                name, r.b[0], r.b[1], Math.exp(r.b[0]), r.b[1], r.correlationIndex);
    }

    private boolean anovaPrint(String name, Stats.AnovaResult a) {
        out.printf("%-6s Dфакт = %.4e, Dзал = %.4e, F = %.2f, Fкр(%d;%d) = %.3f, значущий: %s%n",
                name, a.factorVariance, a.residualVariance, a.f, a.df1, a.df2, a.fCritical,
                a.significant() ? "так" : "ні");
        return a.significant();
    }

    private static final double TARGET_LOSS_FREE = 0.95;

    public static final class LossFreeResult {
        public final double channelTime;
        public final double horizon;

        public final int requiredN95;

        public final int requiredNAll;
        public final double maxOccupancyQ95;
        public final int maxOccupancyMax;

        LossFreeResult(double channelTime, double horizon, int n95, int nAll, double q95, int max) {
            this.channelTime = channelTime;
            this.horizon = horizon;
            this.requiredN95 = n95;
            this.requiredNAll = nAll;
            this.maxOccupancyQ95 = q95;
            this.maxOccupancyMax = max;
        }
    }

    private final Map<String, LossFreeResult> lossFreeCache = new LinkedHashMap<>();

    public List<LossFreeResult> bufferLossFree() {
        out.section(String.format("7. Буфер для роботи без втрат. p = %d.", cfg.lossFreeReps));
        List<LossFreeResult> summary = findRequiredBuffers(ModelParams.base(), cfg.lossFreeHorizons);

        out.println();
        out.printf("%-10s %8s %8s %18s%n", "Tроб, с", "N(0.95)", "N(всі)", "необм. q95 / max");
        List<String> rows = new ArrayList<>();
        for (LossFreeResult r : summary) {
            out.printf("%-10.0f %8s %8s %12.1f / %d%n", r.horizon / 1000, str(r.requiredN95), str(r.requiredNAll),
                    r.maxOccupancyQ95, r.maxOccupancyMax);
            rows.add(Output.row(r.horizon / 1000.0, r.requiredN95, r.requiredNAll, r.maxOccupancyQ95, r.maxOccupancyMax));
        }
        out.csv("buffer_required.csv", "Horizon_s;RequiredBuffer_P095;RequiredBuffer_AllRuns;"
                + "UnlimitedMaxOccupancy_q95;UnlimitedMaxOccupancy_max", rows);
        StringBuilder c = new StringBuilder("для роботи без втрат потрібно:");
        for (LossFreeResult r : summary) {
            c.append(String.format(java.util.Locale.US, " %.0f с - N = %s;", r.horizon / 1000, str(r.requiredNAll)));
        }
        out.conclusion(c.substring(0, c.length() - 1) + ".");
        return summary;
    }

    private static String str(int n) {
        return n < 0 ? "-" : String.valueOf(n);
    }

    private List<LossFreeResult> findRequiredBuffers(ModelParams template, double[] horizons) {
        double channelTime = template.channelTime;
        String variant = String.format(java.util.Locale.US, "ch%.1f", channelTime)
                + (template.duplexSatellite ? "_duplexsat" : "");
        List<LossFreeResult> summary = new ArrayList<>();
        int startIdx = 0;
        for (double h : horizons) {
            String key = variant + "/" + h;
            if (lossFreeCache.containsKey(key)) {
                summary.add(lossFreeCache.get(key));
                out.printf("%nTроб = %.0f с, tкан = %.1f мс: N(0.95) = %s, N(всі) = %s (пораховано вище)%n",
                        h / 1000, channelTime, str(lossFreeCache.get(key).requiredN95), str(lossFreeCache.get(key).requiredNAll));
                continue;
            }
            out.println();
            out.printf("Tроб = %.0f с, tкан = %.1f мс%s.%n", h / 1000,
                    channelTime, template.duplexSatellite ? ", дуплексний супутник" : "");
            out.printf("%5s %12s %8s %12s %12s %10s%n", "N", "без втрат", "P0", "втрат", "викликів", "Uсуп");
            List<String> rows = new ArrayList<>();
            int n95 = -1;
            int nAll = -1;
            int c95 = 0;
            int cAll = 0;
            int first95 = -1;
            int firstAll = -1;
            int nextStart = startIdx;
            for (int gi = startIdx; gi < cfg.bufferGrid.length; gi++) {
                int n = cfg.bufferGrid[gi];
                ModelParams p = template.withBufferSize(n);
                List<SimulationResult> rs = runner.replicate(p, cfg.lossFreeReps, h, 0);
                long zero = rs.stream().filter(r -> r.lostTotal() == 0).count();
                double frac = (double) zero / rs.size();
                double lost = rs.stream().mapToDouble(SimulationResult::lostTotal).average().orElse(0);
                double calls = rs.stream().mapToDouble(SimulationResult::satCallAttempts).average().orElse(0);
                double u = rs.stream().mapToDouble(r -> r.satUtilization).average().orElse(0);
                out.printf("%5d %6d / %-3d %8.3f %12.3f %12.2f %10.6f%n", n, zero, rs.size(), frac, lost, calls, u);
                rows.add(Output.row(n, zero, rs.size(), frac, lost, calls, u));
                if (n95 < 0) {
                    if (frac >= TARGET_LOSS_FREE) {
                        if (c95 == 0) {
                            first95 = gi;
                        }
                        if (++c95 == 2) {
                            n95 = cfg.bufferGrid[first95];
                            nextStart = first95;
                        }
                    } else {
                        c95 = 0;
                    }
                }
                if (nAll < 0) {
                    if (zero == rs.size()) {
                        if (cAll == 0) {
                            firstAll = gi;
                        }
                        if (++cAll == 2) {
                            nAll = cfg.bufferGrid[firstAll];
                        }
                    } else {
                        cAll = 0;
                    }
                }
                if (n95 >= 0 && nAll >= 0) {
                    break;
                }
            }
            startIdx = nextStart;
            out.csv(String.format(java.util.Locale.US, "buffer_lossfree_%s_T%.0fs.csv", variant, h / 1000),
                    "BufferSize;ZeroLossRuns;Runs;ZeroLossFraction;MeanLost;MeanSatCalls;SatUtilization", rows);

            ModelParams unlimited = template.withBufferSize(1_000_000);
            List<SimulationResult> ur = runner.replicate(unlimited, cfg.lossFreeReps, h, 0);
            double[] mx = ur.stream().mapToDouble(SimulationResult::maxBufferOccupancyBoth).toArray();
            double q95 = Stats.quantile(mx, 0.95);
            int max = (int) Stats.max(mx);
            out.printf("N(0.95) = %s, N(всі) = %s%n", str(n95), str(nAll));
            out.printf("Необмежений буфер, макс. заповнення: сер. = %.1f, q95 = %.1f, max = %d%n",
                    Stats.mean(mx), q95, max);
            LossFreeResult res = new LossFreeResult(channelTime, h, n95, nAll, q95, max);
            lossFreeCache.put(key, res);
            summary.add(res);
        }
        return summary;
    }

    public void proposals() {
        out.section(String.format("8. Пропозиція: швидший канал. Tроб = %.0f с.", cfg.proposalHorizon / 1000));
        List<String> rows = new ArrayList<>();
        List<Object[]> table = new ArrayList<>();
        for (double ct : cfg.proposalChannelTimes) {
            List<SimulationResult> base = runner.replicate(ModelParams.base().withChannelTime(ct), cfg.minReplications,
                    cfg.warmup + cfg.observation, cfg.warmup);
            double f = base.stream().mapToDouble(SimulationResult::satCallFrequency).average().orElse(0);
            double u = base.stream().mapToDouble(r -> r.satUtilization).average().orElse(0);
            double pl = base.stream().mapToDouble(SimulationResult::lossProbability).average().orElse(0);
            double occ = base.stream().mapToDouble(r -> (r.meanBufferOccupancy[0] + r.meanBufferOccupancy[1]) / 2)
                    .average().orElse(0);
            LossFreeResult r = findRequiredBuffers(ModelParams.base().withChannelTime(ct),
                    new double[]{cfg.proposalHorizon}).get(0);
            table.add(new Object[]{ct, f, u, pl, occ, r});
            rows.add(Output.row(ct, ct / 10.0, f, u, pl, occ, r.requiredN95, r.requiredNAll, r.maxOccupancyQ95));
        }
        out.println();
        out.printf("Порівняння. N = 2, p = %d, Tп = %.0f мс, Tмод = %.0f мс.%n",
                cfg.minReplications, cfg.warmup, cfg.observation);
        out.printf("%10s %6s %12s %12s %12s %10s %10s %10s%n", "tкан, мс", "rho", "f, 1/с", "Uсуп", "Pвтр", "Буф.сер",
                "N(0.95)", "N(всі)");
        for (Object[] t : table) {
            LossFreeResult r = (LossFreeResult) t[5];
            out.printf("%10.1f %6.2f %12.4f %12.6f %12.3e %10.4f %10s %10s%n", (double) t[0], (double) t[0] / 10.0,
                    (double) t[1], (double) t[2], (double) t[3], (double) t[4], str(r.requiredN95), str(r.requiredNAll));
        }
        out.csv("proposals.csv", "ChannelTime_ms;Rho;SatCallFreq_N2;SatUtil_N2;LossProb_N2;MeanBuffer_N2;"
                + "RequiredBuffer_P095;RequiredBuffer_AllRuns;UnlimitedMaxOcc_q95", rows);
        Object[] a = table.get(0);
        Object[] z = table.get(table.size() - 1);
        out.conclusion(String.format(java.util.Locale.US,
                "при tкан %.1f -> %.1f мс f змінюється %.3f -> %.3f 1/с, потрібний N(всі) %s -> %s.",
                (double) a[0], (double) z[0], (double) a[1], (double) z[1],
                str(((LossFreeResult) a[5]).requiredNAll), str(((LossFreeResult) z[5]).requiredNAll)));
        duplexSatellite();
    }

    public void duplexSatellite() {
        out.section(String.format("8б. Пропозиція: дуплексний супутник. Tроб = %.0f с.", cfg.proposalHorizon / 1000));
        ModelParams half = ModelParams.base();
        ModelParams duplex = ModelParams.base().withDuplexSatellite(true);
        List<String> rows = new ArrayList<>();
        List<Double> lossPerHour = new ArrayList<>();
        out.printf("Сталий режим. p = %d, Tп = %.0f мс, Tмод = %.0f мс.%n", cfg.minReplications, cfg.warmup,
                cfg.observation);
        out.printf("%-16s %4s %10s %12s %12s %12s %12s%n", "Супутник", "N", "f, 1/с", "fобсл, 1/с",
                "Uсуп", "Pвтр", "втрат/год");
        for (ModelParams p : new ModelParams[]{half, duplex, duplex.withBufferSize(1), half.withBufferSize(1)}) {
            List<SimulationResult> rs = runner.replicate(p, cfg.minReplications, cfg.warmup + cfg.observation,
                    cfg.warmup);
            double f = rs.stream().mapToDouble(SimulationResult::satCallFrequency).average().orElse(0);
            double fs = rs.stream().mapToDouble(SimulationResult::satServedFrequency).average().orElse(0);
            double u = rs.stream().mapToDouble(r -> r.satUtilization).average().orElse(0);
            double pl = rs.stream().mapToDouble(SimulationResult::lossProbability).average().orElse(0);
            double lh = rs.stream().mapToDouble(r -> r.lostTotal() / r.observedTime() * 3_600_000).average().orElse(0);
            String name = p.duplexSatellite ? "дуплексний" : "напівдуплексний";
            out.printf("%-16s %4d %10.4f %12.4f %12.6f %12.3e %12.2f%n", name, p.bufferSize, f, fs, u, pl, lh);
            rows.add(Output.row(p.duplexSatellite ? "duplex" : "half-duplex", p.bufferSize, f, fs, u, pl, lh));
            lossPerHour.add(lh);
        }
        out.csv("proposal_duplex.csv", "Satellite;BufferSize;SatCallFreq_per_s;SatServedFreq_per_s;SatUtilPerChannel;"
                + "LossProb;LostPerHour", rows);

        out.println();
        out.printf("Мінімальний проміжок між переповненнями одного пункту. Прогонів = %d.%n", cfg.lossFreeReps / 3);
        out.printf("%3s %14s %12s %14s%n", "N", "проміжок, мс", "межа, мс", "макс. Sat, мс");
        List<String> gapRows = new ArrayList<>();
        boolean gapOk = true;
        for (int n : new int[]{1, 2, 3}) {
            ModelParams p = duplex.withBufferSize(n);
            List<Callable<Double>> tasks = new ArrayList<>();
            for (int i = 0; i < cfg.lossFreeReps / 3; i++) {
                tasks.add(() -> minOverflowGap(p, cfg.proposalHorizon));
            }
            double gap = runner.runAll(tasks).stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            double bound = (n >= 2 ? 3 : 2) * (p.arrivalMean - p.arrivalDev);
            out.printf("%3d %14.3f %12.0f %14.0f%n", n, gap, bound, p.satMean + p.satDev);
            gapRows.add(Output.row(n, gap, bound, p.satMean + p.satDev));
            if (n >= 2) {
                gapOk &= gap > p.satMean + p.satDev;
            }
        }
        out.csv("proposal_duplex_gap.csv", "BufferSize;MinOverflowGap_ms;AnalyticBound_ms;MaxSatTime_ms", gapRows);

        D3Simulation s = new D3Simulation(duplex);
        InvariantChecker inv = new InvariantChecker(s.net());
        s.addObserver(inv);
        SimulationResult r = s.run(cfg.proposalHorizon, 0);
        out.printf("%nІнваріанти: перевірок = %d, порушень = %d, баланс: %s, втрачено = %d%n", inv.checks(),
                inv.violations().size(), r.balanceHolds() ? "так" : "ні", r.lostTotal());
        for (String v : inv.violations()) {
            out.println("      ! " + v);
        }

        LossFreeResult lf = findRequiredBuffers(duplex, new double[]{cfg.proposalHorizon}).get(0);
        out.printf("%nДуплексний супутник: N(0.95) = %s, N(всі) = %s%n", str(lf.requiredN95), str(lf.requiredNAll));
        out.conclusion(String.format(java.util.Locale.US,
                "при N = 2 втрат за годину: напівдуплексний %.1f, дуплексний %.1f; при N >= 2 проміжок між "
                        + "переповненнями %s за найбільший час передачі супутником.",
                lossPerHour.get(0), lossPerHour.get(1), gapOk ? "більший" : "не завжди більший"));
    }

    private static double minOverflowGap(ModelParams p, double horizon) {
        D3Simulation s = new D3Simulation(p);
        var net = s.net();
        for (D3Net.Point pt : net.points()) {
            pt.toSatellite.setMoments(true);
            pt.loss.setMoments(true);
        }
        s.run(horizon, 0);
        double min = Double.POSITIVE_INFINITY;
        for (D3Net.Point pt : net.points()) {
            List<Double> m = new ArrayList<>(pt.toSatellite.getInMoments());
            m.addAll(pt.loss.getInMoments());
            java.util.Collections.sort(m);
            for (int i = 1; i < m.size(); i++) {
                min = Math.min(min, m.get(i) - m.get(i - 1));
            }
        }
        return min;
    }

    public void runAll() {
        protocol();
        verification();
        transientPeriod();
        int p = replications();
        baseline(p);
        callIntervals();
        estimatesDistribution();
        bufferSteady();
        bufferLossFree();
        proposals();
    }
}
