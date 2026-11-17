package ua.kpi.d3.sim;

import ua.kpi.d3.model.ModelParams;

import java.util.Locale;

public final class SimulationResult {
    public final ModelParams params;
    public final double simulationTime;
    public final double warmup;
    public final long events;

    public final int[] arrived = new int[2];
    public final int[] delivered = new int[2];

    public final int[] satCalls = new int[2];
    public final int[] lost = new int[2];
    public int satDelivered;

    public double satUtilization;
    public final double[] channelUtilization = new double[2];
    public final double[] meanQueue = new double[2];
    public final double[] meanBufferOccupancy = new double[2];
    public final int[] maxBufferOccupancy = new int[2];
    public final int[] maxBufferOccupancySteady = new int[2];

    public double libSatFreeMean;
    public double libSatTransmitMean;
    public final double[] libChannelBusyMean = new double[2];
    public final double[] libQueueMean = new double[2];

    public int totalArrived;
    public int totalDelivered;
    public int totalSatDelivered;
    public int totalLost;
    public int inSystemAtEnd;

    SimulationResult(ModelParams params, double simulationTime, double warmup, long events) {
        this.params = params;
        this.simulationTime = simulationTime;
        this.warmup = Math.max(0.0, warmup);
        this.events = events;
    }

    public double observedTime() {
        return simulationTime - warmup;
    }

    public int totalArrivedObserved() {
        return arrived[0] + arrived[1];
    }

    public int satServed() {
        return satCalls[0] + satCalls[1];
    }

    public int lostTotal() {
        return lost[0] + lost[1];
    }

    public int satCallAttempts() {
        return satServed() + lostTotal();
    }

    public double satCallFrequency() {
        return satCallAttempts() / observedTime() * 1000.0;
    }

    public double satServedFrequency() {
        return satServed() / observedTime() * 1000.0;
    }

    public double lossFrequency() {
        return lostTotal() / observedTime() * 1000.0;
    }

    public double lossProbability() {
        int n = totalArrivedObserved();
        return n == 0 ? 0.0 : (double) lostTotal() / n;
    }

    public double overflowProbability() {
        int n = totalArrivedObserved();
        return n == 0 ? 0.0 : (double) satCallAttempts() / n;
    }

    public double satRefusalProbability() {
        int n = satCallAttempts();
        return n == 0 ? 0.0 : (double) lostTotal() / n;
    }

    public double meanTimeInBuffer(int i) {
        return delivered[i] == 0 ? 0.0 : meanBufferOccupancy[i] * observedTime() / delivered[i];
    }

    public double meanTimeInBuffer() {
        int n = delivered[0] + delivered[1];
        return n == 0 ? 0.0 : (meanBufferOccupancy[0] + meanBufferOccupancy[1]) * observedTime() / n;
    }

    public int maxBufferOccupancyBoth() {
        return Math.max(maxBufferOccupancy[0], maxBufferOccupancy[1]);
    }

    public boolean balanceHolds() {
        return totalArrived == totalDelivered + totalSatDelivered + totalLost + inSystemAtEnd;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Параметри: %s%n", params));
        sb.append(String.format(Locale.US, "T = %.0f мс, перехідний період = %.0f мс, подій = %d%n",
                simulationTime, warmup, events));
        sb.append(String.format(Locale.US, "Надійшло: A = %d, B = %d%n", arrived[0], arrived[1]));
        sb.append(String.format(Locale.US, "Передано каналом: A = %d, B = %d%n", delivered[0], delivered[1]));
        sb.append(String.format(Locale.US, "Передано супутником: A = %d, B = %d%n", satCalls[0], satCalls[1]));
        sb.append(String.format(Locale.US, "Втрачено: A = %d, B = %d%n", lost[0], lost[1]));
        sb.append(String.format(Locale.US, "f = %.4f 1/с, Uсуп = %.5f%n", satCallFrequency(), satUtilization));
        sb.append(String.format(Locale.US, "Pвтр = %.6f, Pпереп = %.6f%n", lossProbability(), overflowProbability()));
        sb.append(String.format(Locale.US, "Uкан: A = %.4f, B = %.4f%n", channelUtilization[0], channelUtilization[1]));
        sb.append(String.format(Locale.US, "Буфер сер.: A = %.4f, B = %.4f, макс.: A = %d, B = %d%n",
                meanBufferOccupancy[0], meanBufferOccupancy[1], maxBufferOccupancy[0], maxBufferOccupancy[1]));
        sb.append(String.format(Locale.US, "Час у буфері: A = %.3f мс, B = %.3f мс%n", meanTimeInBuffer(0), meanTimeInBuffer(1)));
        sb.append(String.format(Locale.US, "Баланс: %d = %d + %d + %d + %d (%s)%n",
                totalArrived, totalDelivered, totalSatDelivered, totalLost, inSystemAtEnd, balanceHolds() ? "так" : "ні"));
        return sb.toString();
    }
}
