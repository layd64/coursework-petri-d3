package ua.kpi.d3.model;

import java.util.Locale;

public final class ModelParams {
    public final double arrivalMean;

    public final double arrivalDev;

    public final double channelTime;

    public final int bufferSize;

    public final double satMean;

    public final double satDev;

    public final boolean duplexSatellite;

    public ModelParams(double arrivalMean, double arrivalDev, double channelTime,
                       int bufferSize, double satMean, double satDev) {
        this(arrivalMean, arrivalDev, channelTime, bufferSize, satMean, satDev, false);
    }

    public ModelParams(double arrivalMean, double arrivalDev, double channelTime,
                       int bufferSize, double satMean, double satDev, boolean duplexSatellite) {
        if (arrivalMean - arrivalDev <= 0 || satMean - satDev < 0 || channelTime < 0 || bufferSize < 1) {
            throw new IllegalArgumentException("Некоректні параметри моделі: " + describe(arrivalMean, arrivalDev,
                    channelTime, bufferSize, satMean, satDev));
        }
        this.arrivalMean = arrivalMean;
        this.arrivalDev = arrivalDev;
        this.channelTime = channelTime;
        this.bufferSize = bufferSize;
        this.satMean = satMean;
        this.satDev = satDev;
        this.duplexSatellite = duplexSatellite;
    }

    public static ModelParams base() {
        return new ModelParams(10.0, 3.0, 10.0, 2, 10.0, 5.0);
    }

    public ModelParams withArrival(double mean, double dev) {
        return new ModelParams(mean, dev, channelTime, bufferSize, satMean, satDev, duplexSatellite);
    }

    public ModelParams withChannelTime(double t) {
        return new ModelParams(arrivalMean, arrivalDev, t, bufferSize, satMean, satDev, duplexSatellite);
    }

    public ModelParams withBufferSize(int n) {
        return new ModelParams(arrivalMean, arrivalDev, channelTime, n, satMean, satDev, duplexSatellite);
    }

    public ModelParams withSatellite(double mean, double dev) {
        return new ModelParams(arrivalMean, arrivalDev, channelTime, bufferSize, mean, dev, duplexSatellite);
    }

    public ModelParams withDuplexSatellite(boolean duplex) {
        return new ModelParams(arrivalMean, arrivalDev, channelTime, bufferSize, satMean, satDev, duplex);
    }

    public double channelLoad() {
        return channelTime / arrivalMean;
    }

    @Override
    public String toString() {
        return describe(arrivalMean, arrivalDev, channelTime, bufferSize, satMean, satDev)
                + (duplexSatellite ? ", супутник дуплексний" : "");
    }

    private static String describe(double am, double ad, double ct, int n, double sm, double sd) {
        return String.format(Locale.US, "надходження=unif(%.1f;%.1f) мс, канал=%.1f мс, буфер=%d, супутник=unif(%.1f;%.1f) мс",
                am, ad, ct, n, sm, sd);
    }
}
