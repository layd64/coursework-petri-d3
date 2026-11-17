package ua.kpi.d3.experiment;

import ua.kpi.d3.model.ModelParams;
import ua.kpi.d3.sim.SimulationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class FactorialExperiment {
    private static final double ALPHA = 0.05;

    private static final String[] COLUMNS = {"x0", "x1", "x2", "x3", "x1x2", "x1x3", "x2x3", "x1x2x3"};

    private final ExperimentConfig cfg;
    private final Output out;
    private final Runner runner;

    public FactorialExperiment(ExperimentConfig cfg, Output out, Runner runner) {
        this.cfg = cfg;
        this.out = out;
        this.runner = runner;
    }

    public void run() {
        double[][] levels = {
                {cfg.factN[0], cfg.factN[1]},
                {cfg.factChannel[0], cfg.factChannel[1]},
                {cfg.factSat[0], cfg.factSat[1]}};
        String[] names = {"X1 (N)", "X2 (tкан, мс)", "X3 (tсуп, мс)"};
        int k = 3;
        int n = 1 << k;
        int p = cfg.factorialReps;
        double total = cfg.warmup + cfg.observation;

        out.section(String.format("9. Факторний експеримент 2^3. p = %d, Tп = %.0f мс.", p, cfg.warmup));
        out.printf("%-14s %6s %6s %6s %6s%n", "фактор", "Xmin", "Xmax", "X0", "dX");
        for (int i = 0; i < k; i++) {
            double x0 = (levels[i][0] + levels[i][1]) / 2;
            double dx = (levels[i][1] - levels[i][0]) / 2;
            out.printf("%-14s %6.1f %6.1f %6.2f %6.2f%n",
                    names[i], levels[i][0], levels[i][1], x0, dx);
        }

        int[][] x = new int[n][COLUMNS.length];
        for (int j = 0; j < n; j++) {
            int x1 = (j & 1) == 0 ? -1 : 1;
            int x2 = (j & 2) == 0 ? -1 : 1;
            int x3 = (j & 4) == 0 ? -1 : 1;
            x[j] = new int[]{1, x1, x2, x3, x1 * x2, x1 * x3, x2 * x3, x1 * x2 * x3};
        }

        ModelParams[] mp = new ModelParams[n];
        for (int j = 0; j < n; j++) {
            int nb = (int) levels[0][x[j][1] < 0 ? 0 : 1];
            double ch = levels[1][x[j][2] < 0 ? 0 : 1];
            double sat = levels[2][x[j][3] < 0 ? 0 : 1];
            mp[j] = ModelParams.base().withBufferSize(nb).withChannelTime(ch).withSatellite(sat, 5.0);
        }

        out.printf("Пробна серія: %d прогонів, Tмод = %.0f мс.%n", cfg.factorialPilot, cfg.observation);
        double[][] pilotVar = new double[2][n];
        for (int j = 0; j < n; j++) {
            List<SimulationResult> pr = runner.replicate(mp[j], cfg.factorialPilot, total, cfg.warmup);
            double satMean = mp[j].satMean;
            double es2 = satMean * satMean + mp[j].satDev * mp[j].satDev / 3.0;
            pilotVar[0][j] = pr.stream().mapToDouble(SimulationResult::satCallFrequency).average().orElse(1);
            pilotVar[1][j] = pr.stream().mapToDouble(r -> r.satUtilization).average().orElse(1) * es2 / satMean;
        }

        String[] titles = {"f, 1/с", "Uсуп"};
        String[] keys = {"f", "U"};
        List<ToDoubleFunction<SimulationResult>> responses = List.of(SimulationResult::satCallFrequency,
                r -> r.satUtilization);
        for (int r = 0; r < 2; r++) {
            double minVar = Double.MAX_VALUE;
            for (double v : pilotVar[r]) {
                minVar = Math.min(minVar, v);
            }
            double[] obs = new double[n];
            for (int j = 0; j < n; j++) {
                obs[j] = Math.ceil(cfg.observation * pilotVar[r][j] / minVar / 1000.0) * 1000.0;
            }
            List<List<SimulationResult>> results = series(mp, obs, p);
            double[] g = cochran(results, responses.get(r));
            if (g[0] >= g[1]) {
                out.printf("%nВідгук %s: G = %.4f >= Gкр = %.4f, повтор з p = %d%n", keys[r], g[0], g[1], 2 * p);
                results = series(mp, obs, 2 * p);
            }
            process(titles[r], keys[r], results, x, levels, obs, responses.get(r));
        }
    }

    private List<List<SimulationResult>> series(ModelParams[] mp, double[] obs, int p) {
        List<List<SimulationResult>> results = new ArrayList<>();
        for (int j = 0; j < mp.length; j++) {
            results.add(runner.replicate(mp[j], p, cfg.warmup + obs[j], cfg.warmup));
        }
        return results;
    }

    private static double[] cochran(List<List<SimulationResult>> results, ToDoubleFunction<SimulationResult> response) {
        double sum = 0;
        double max = 0;
        for (List<SimulationResult> rs : results) {
            double s = Stats.std(rs.stream().mapToDouble(response).toArray());
            sum += s * s;
            max = Math.max(max, s * s);
        }
        return new double[]{max / sum, Stats.cochranCritical(ALPHA, results.size(), results.get(0).size() - 1)};
    }

    private void process(String title, String key, List<List<SimulationResult>> results, int[][] x,
                         double[][] levels, double[] obs, ToDoubleFunction<SimulationResult> response) {
        int n = x.length;
        int m = COLUMNS.length;
        int p = results.get(0).size();
        double[] mean = new double[n];
        double[] var = new double[n];
        for (int j = 0; j < n; j++) {
            double[] y = results.get(j).stream().mapToDouble(response).toArray();
            mean[j] = Stats.mean(y);
            double s = Stats.std(y);
            var[j] = s * s;
        }

        out.println();
        out.println("Відгук: " + title);
        out.printf("%3s %4s %4s %4s %4s %5s %5s %5s %7s | %5s %6s %6s %10s | %12s %12s%n", "№", "x0", "x1", "x2", "x3",
                "x1x2", "x1x3", "x2x3", "x1x2x3", "N", "tкан", "tсуп", "T_j, мс", "y сер.", "D_j");
        List<String> rows = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            out.printf("%3d %4s %4s %4s %4s %5s %5s %5s %7s | %5.0f %6.1f %6.1f %10.0f | %12.6f %12.4e%n", j + 1,
                    sign(x[j][0]), sign(x[j][1]), sign(x[j][2]), sign(x[j][3]), sign(x[j][4]), sign(x[j][5]),
                    sign(x[j][6]), sign(x[j][7]),
                    levels[0][x[j][1] < 0 ? 0 : 1], levels[1][x[j][2] < 0 ? 0 : 1], levels[2][x[j][3] < 0 ? 0 : 1],
                    obs[j], mean[j], var[j]);
            rows.add(Output.row(j + 1, x[j][1], x[j][2], x[j][3], levels[0][x[j][1] < 0 ? 0 : 1],
                    levels[1][x[j][2] < 0 ? 0 : 1], levels[2][x[j][3] < 0 ? 0 : 1], obs[j], mean[j], var[j]));
        }
        out.csv("factorial_" + key + ".csv", "No;x1;x2;x3;BufferSize;ChannelTime_ms;SatMean_ms;ObservationTime_ms;"
                + "MeanResponse;Variance", rows);

        double sumVar = 0;
        double maxVar = 0;
        for (double v : var) {
            sumVar += v;
            maxVar = Math.max(maxVar, v);
        }
        double g = maxVar / sumVar;
        double gCr = Stats.cochranCritical(ALPHA, n, p - 1);
        out.printf("Кохрен: G = %.4f, Gкр(%d; %d) = %.4f, відтворюваний: %s%n",
                g, n, p - 1, gCr, g < gCr ? "так" : "ні");

        double d = sumVar / n;
        int df = n * (p - 1);
        double tCr = Stats.tCritical(ALPHA, df);
        double[] b = new double[m];
        boolean[] significant = new boolean[m];
        int l = 0;
        out.printf("Ст'юдент: D = %.4e, tкр(%d) = %.3f%n", d, df, tCr);
        for (int c = 0; c < m; c++) {
            for (int j = 0; j < n; j++) {
                b[c] += mean[j] * x[j][c];
            }
            b[c] /= n;
            double t = Math.abs(b[c]) * Math.sqrt((double) n * p) / Math.sqrt(d);
            significant[c] = t > tCr;
            if (significant[c]) {
                l++;
            }
            out.printf("b(%-6s) = %12.6f   t = %10.2f   %s%n", COLUMNS[c], b[c], t,
                    significant[c] ? "значущий" : "незначущий");
        }
        StringBuilder eq = new StringBuilder("y = ");
        boolean first = true;
        for (int c = 0; c < m; c++) {
            if (!significant[c]) {
                continue;
            }
            String term = c == 0 ? String.format(java.util.Locale.US, "%.5f", b[c])
                    : String.format(java.util.Locale.US, "%.5f*%s", Math.abs(b[c]), COLUMNS[c]);
            eq.append(first ? (b[c] < 0 && c > 0 ? "-" : "") : (b[c] < 0 ? " - " : " + ")).append(term);
            first = false;
        }
        out.println(eq.toString());

        boolean adequate = true;
        if (l == m) {
            out.println("Фішер: усі коефіцієнти значущі, перевірка не потрібна");
        } else {
            double ss = 0;
            for (int j = 0; j < n; j++) {
                double yr = 0;
                for (int c = 0; c < m; c++) {
                    if (significant[c]) {
                        yr += b[c] * x[j][c];
                    }
                }
                ss += (mean[j] - yr) * (mean[j] - yr);
            }
            double dAd = p * ss / (n - l);
            double f = dAd / d;
            double fCr = Stats.fQuantile(1 - ALPHA, n - l, df);
            out.printf("Фішер: Dад = %.4e, L = %d, F = %.3f, Fкр(%d; %d) = %.3f, адекватне: %s%n", dAd, l, f, n - l, df, fCr,
                    f < fCr ? "так" : "ні");
            adequate = f < fCr;
        }

        String[] fn = {"N", "tкан", "tсуп"};
        out.print("Вплив 2b: ");
        for (int i = 1; i <= 3; i++) {
            out.printf("%s = %+.5f", fn[i - 1], 2 * b[i]);
            out.print(i < 3 ? ", " : "\n");
        }
        StringBuilder sig = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            if (significant[i]) {
                sig.append(sig.length() > 0 ? ", " : "").append(fn[i - 1]);
            }
        }
        out.conclusion("значущі фактори: " + (sig.length() > 0 ? sig : "немає") + "; рівняння регресії "
                + (adequate ? "адекватне." : "неадекватне."));
    }

    private static String sign(int v) {
        return v > 0 ? "+" : "-";
    }
}
