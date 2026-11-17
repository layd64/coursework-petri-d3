package ua.kpi.d3.experiment;

import java.util.Arrays;

public final class Stats {
    private Stats() {
    }

    public static double mean(double[] x) {
        double s = 0;
        for (double v : x) {
            s += v;
        }
        return s / x.length;
    }

    public static double std(double[] x) {
        if (x.length < 2) {
            return 0.0;
        }
        double m = mean(x);
        double s = 0;
        for (double v : x) {
            s += (v - m) * (v - m);
        }
        return Math.sqrt(s / (x.length - 1));
    }

    public static double min(double[] x) {
        return Arrays.stream(x).min().orElse(Double.NaN);
    }

    public static double max(double[] x) {
        return Arrays.stream(x).max().orElse(Double.NaN);
    }

    public static double quantile(double[] x, double q) {
        double[] s = x.clone();
        Arrays.sort(s);
        double pos = q * (s.length - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(i + 1, s.length - 1);
        return s[i] + (pos - i) * (s[j] - s[i]);
    }

    public static int chebyshevReplications(double sigma, double eps, double beta) {
        return (int) Math.ceil(sigma * sigma / (eps * eps * (1 - beta)));
    }

    public static double chebyshevHalfWidth(double sigma, int p, double beta) {
        return sigma / Math.sqrt(p * (1 - beta));
    }

    public static double chiSquareQuantile(double prob, int df) {
        double z = normalQuantile(prob);
        double a = 2.0 / (9.0 * df);
        return df * Math.pow(1 - a + z * Math.sqrt(a), 3);
    }

    public static double normalCdf(double x) {
        double erf = regularizedGammaP(0.5, x * x / 2);
        return x >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
    }

    public static double normalQuantile(double p) {
        double q = p < 0.5 ? p : 1 - p;
        double t = Math.sqrt(-2 * Math.log(q));
        double z = t - (2.515517 + 0.802853 * t + 0.010328 * t * t)
                / (1 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
        return p < 0.5 ? -z : z;
    }

    public static double tCritical(double alpha, int df) {
        return Math.sqrt(fQuantile(1 - alpha, 1, df));
    }

    public static double cochranCritical(double alpha, int n, int m) {
        double f = fQuantile(1 - alpha / n, m, (n - 1) * m);
        return f / (f + n - 1);
    }

    public static final class AnovaResult {
        public final int levels;
        public final int replications;
        public final double factorVariance;
        public final double residualVariance;
        public final double f;
        public final double fCritical;
        public final int df1;
        public final int df2;

        AnovaResult(int levels, int replications, double factorVariance, double residualVariance,
                    double f, double fCritical, int df1, int df2) {
            this.levels = levels;
            this.replications = replications;
            this.factorVariance = factorVariance;
            this.residualVariance = residualVariance;
            this.f = f;
            this.fCritical = fCritical;
            this.df1 = df1;
            this.df2 = df2;
        }

        public boolean significant() {
            return f > fCritical;
        }
    }

    public static AnovaResult anova(double[][] y, double alpha) {
        int q = y.length;
        int p = y[0].length;
        double[] levelMeans = new double[q];
        double grand = 0;
        for (int j = 0; j < q; j++) {
            levelMeans[j] = mean(y[j]);
            grand += levelMeans[j];
        }
        grand /= q;
        double ssFactor = 0;
        double ssResidual = 0;
        for (int j = 0; j < q; j++) {
            ssFactor += p * (levelMeans[j] - grand) * (levelMeans[j] - grand);
            for (int s = 0; s < p; s++) {
                ssResidual += (y[j][s] - levelMeans[j]) * (y[j][s] - levelMeans[j]);
            }
        }
        int df1 = q - 1;
        int df2 = q * (p - 1);
        double dFactor = ssFactor / df1;
        double dResidual = ssResidual / df2;
        double f = dFactor / dResidual;
        return new AnovaResult(q, p, dFactor, dResidual, f, fQuantile(1 - alpha, df1, df2), df1, df2);
    }

    public static double fQuantile(double prob, int d1, int d2) {
        double lo = 0;
        double hi = 1;
        while (fCdf(hi, d1, d2) < prob) {
            hi *= 2;
        }
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (lo + hi);
            if (fCdf(mid, d1, d2) < prob) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    public static double fCdf(double x, int d1, int d2) {
        if (x <= 0) {
            return 0;
        }
        double z = d1 * x / (d1 * x + d2);
        return regularizedBeta(z, d1 / 2.0, d2 / 2.0);
    }

    private static double regularizedBeta(double x, double a, double b) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double bt = Math.exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * Math.log(x) + b * Math.log(1 - x));
        if (x < (a + 1) / (a + b + 2)) {
            return bt * betaContinuedFraction(x, a, b) / a;
        }
        return 1 - bt * betaContinuedFraction(1 - x, b, a) / b;
    }

    private static double betaContinuedFraction(double x, double a, double b) {
        final double eps = 1e-14;
        final double fpmin = 1e-300;
        double qab = a + b;
        double qap = a + 1;
        double qam = a - 1;
        double c = 1;
        double d = 1 - qab * x / qap;
        if (Math.abs(d) < fpmin) {
            d = fpmin;
        }
        d = 1 / d;
        double h = d;
        for (int m = 1; m <= 1000; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < fpmin) {
                d = fpmin;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < fpmin) {
                c = fpmin;
            }
            d = 1 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d;
            if (Math.abs(d) < fpmin) {
                d = fpmin;
            }
            c = 1 + aa / c;
            if (Math.abs(c) < fpmin) {
                c = fpmin;
            }
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < eps) {
                break;
            }
        }
        return h;
    }

    public static double regularizedGammaP(double a, double x) {
        if (x <= 0) {
            return 0;
        }
        if (x < a + 1) {
            double sum = 1.0 / a;
            double del = sum;
            double ap = a;
            for (int n = 0; n < 1000; n++) {
                ap += 1;
                del *= x / ap;
                sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-14) {
                    break;
                }
            }
            return sum * Math.exp(-x + a * Math.log(x) - lnGamma(a));
        }

        double b = x + 1 - a;
        double c = 1 / 1e-300;
        double d = 1 / b;
        double h = d;
        for (int i = 1; i < 1000; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < 1e-300) {
                d = 1e-300;
            }
            c = b + an / c;
            if (Math.abs(c) < 1e-300) {
                c = 1e-300;
            }
            d = 1 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1) < 1e-14) {
                break;
            }
        }
        return 1 - Math.exp(-x + a * Math.log(x) - lnGamma(a)) * h;
    }

    private static double lnGamma(double x) {
        double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double c : cof) {
            ser += c / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }

    public static final class RegressionResult {
        public final double[] b;

        public final double correlationIndex;
        public final double[] fitted;

        RegressionResult(double[] b, double correlationIndex, double[] fitted) {
            this.b = b;
            this.correlationIndex = correlationIndex;
            this.fitted = fitted;
        }
    }

    public static RegressionResult regression(double[][] x, double[] y) {
        int n = y.length;
        int m = x[0].length + 1;
        double[][] xm = new double[n][m];
        for (int i = 0; i < n; i++) {
            xm[i][0] = 1;
            System.arraycopy(x[i], 0, xm[i], 1, m - 1);
        }
        double[][] xtx = new double[m][m];
        double[] xty = new double[m];
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < m; a++) {
                xty[a] += xm[i][a] * y[i];
                for (int c = 0; c < m; c++) {
                    xtx[a][c] += xm[i][a] * xm[i][c];
                }
            }
        }
        double[] b = solve(xtx, xty);
        double[] fitted = new double[n];
        double ym = mean(y);
        double ssRes = 0;
        double ssTot = 0;
        for (int i = 0; i < n; i++) {
            for (int a = 0; a < m; a++) {
                fitted[i] += b[a] * xm[i][a];
            }
            ssRes += (y[i] - fitted[i]) * (y[i] - fitted[i]);
            ssTot += (y[i] - ym) * (y[i] - ym);
        }
        double r = ssTot == 0 ? 1.0 : Math.sqrt(Math.max(0, 1 - ssRes / ssTot));
        return new RegressionResult(b, r, fitted);
    }

    private static double[] solve(double[][] a0, double[] b0) {
        int n = b0.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = a0[i].clone();
        }
        double[] b = b0.clone();
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[piv][col])) {
                    piv = r;
                }
            }
            double[] t = a[col];
            a[col] = a[piv];
            a[piv] = t;
            double tb = b[col];
            b[col] = b[piv];
            b[piv] = tb;
            for (int r = col + 1; r < n; r++) {
                double k = a[r][col] / a[col][col];
                for (int c = col; c < n; c++) {
                    a[r][c] -= k * a[col][c];
                }
                b[r] -= k * b[col];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < n; c++) {
                s -= a[r][c] * x[c];
            }
            x[r] = s / a[r][r];
        }
        return x;
    }
}
