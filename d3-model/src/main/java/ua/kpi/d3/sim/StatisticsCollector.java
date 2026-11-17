package ua.kpi.d3.sim;

import ua.kpi.d3.model.D3Net;

public final class StatisticsCollector implements SimulationObserver {
    private final D3Net net;
    private final double warmup;
    private final int bufferSize;

    private double lastTime = 0.0;
    private boolean warmupSnapshotTaken;

    private double intSatBusy;
    private final double[] intQueue = new double[2];
    private final double[] intBufOcc = new double[2];
    private final double[] intChBusy = new double[2];

    private final int[] maxBufOccTotal = new int[2];
    private final int[] maxBufOccSteady = new int[2];

    private final int[] arrived0 = new int[2];
    private final int[] delivered0 = new int[2];
    private final int[] satCalls0 = new int[2];
    private final int[] lost0 = new int[2];
    private int satDelivered0;

    private long events;
    private double endTime;

    public StatisticsCollector(D3Net net, double warmup) {
        this.net = net;
        this.warmup = warmup;
        this.bufferSize = net.params.bufferSize;
        this.warmupSnapshotTaken = warmup <= 0.0;
    }

    @Override
    public void onEvent(double time, String eventName) {
        events++;
        integrate(time);
        updateMax(time);
    }

    @Override
    public void onFinish(double endTime) {
        this.endTime = endTime;
        integrate(endTime);
        updateMax(endTime);
    }

    private void integrate(double time) {
        // інтеграл маркування рахується тільки після перехідного періоду
        if (!warmupSnapshotTaken && time >= warmup) {
            takeSnapshot();
        }
        double from = Math.max(lastTime, warmup);
        double dt = time - from;
        if (dt > 0) {
            intSatBusy += net.satBusy() * dt;
            D3Net.Point[] pts = net.points();
            for (int i = 0; i < 2; i++) {
                D3Net.Point p = pts[i];
                intQueue[i] += p.queue.getMark() * dt;
                intBufOcc[i] += (bufferSize - p.bufFree.getMark()) * dt;
                intChBusy[i] += (1 - p.chFree.getMark()) * dt;
            }
        }
        if (time > lastTime) {
            lastTime = time;
        }
    }

    private void updateMax(double time) {
        D3Net.Point[] pts = net.points();
        for (int i = 0; i < 2; i++) {
            int occ = bufferSize - pts[i].bufFree.getMark();
            maxBufOccTotal[i] = Math.max(maxBufOccTotal[i], occ);
            if (time >= warmup) {
                maxBufOccSteady[i] = Math.max(maxBufOccSteady[i], occ);
            }
        }
    }

    private void takeSnapshot() {
        D3Net.Point[] pts = net.points();
        for (int i = 0; i < 2; i++) {
            arrived0[i] = pts[i].arrived.getMark();
            delivered0[i] = pts[i].delivered.getMark();
            satCalls0[i] = pts[i].satCalls.getMark();
            lost0[i] = pts[i].lost.getMark();
        }
        satDelivered0 = net.satDelivered();
        warmupSnapshotTaken = true;
    }

    public SimulationResult result() {
        double observed = endTime - Math.max(0.0, warmup);
        D3Net.Point[] pts = net.points();
        SimulationResult r = new SimulationResult(net.params, endTime, warmup, events);
        for (int i = 0; i < 2; i++) {
            r.arrived[i] = pts[i].arrived.getMark() - arrived0[i];
            r.delivered[i] = pts[i].delivered.getMark() - delivered0[i];
            r.satCalls[i] = pts[i].satCalls.getMark() - satCalls0[i];
            r.lost[i] = pts[i].lost.getMark() - lost0[i];
            r.meanQueue[i] = intQueue[i] / observed;
            r.meanBufferOccupancy[i] = intBufOcc[i] / observed;
            r.channelUtilization[i] = intChBusy[i] / observed;
            r.maxBufferOccupancy[i] = maxBufOccTotal[i];
            r.maxBufferOccupancySteady[i] = maxBufOccSteady[i];
        }
        int k = net.satellites.length;
        r.satDelivered = net.satDelivered() - satDelivered0;

        r.satUtilization = intSatBusy / observed / k;
        for (D3Net.Satellite s : net.satellites) {
            r.libSatFreeMean += s.free.getMean() / k;
            r.libSatTransmitMean += s.transmit.getMean() / k;
        }
        r.libChannelBusyMean[0] = net.a.transmit.getMean();
        r.libChannelBusyMean[1] = net.b.transmit.getMean();
        r.libQueueMean[0] = net.a.queue.getMean();
        r.libQueueMean[1] = net.b.queue.getMean();
        r.inSystemAtEnd = inSystemNow();
        r.totalArrived = pts[0].arrived.getMark() + pts[1].arrived.getMark();
        r.totalDelivered = pts[0].delivered.getMark() + pts[1].delivered.getMark();
        r.totalSatDelivered = net.satDelivered();
        r.totalLost = pts[0].lost.getMark() + pts[1].lost.getMark();
        return r;
    }

    private int inSystemNow() {
        int n = 0;
        for (D3Net.Point p : net.points()) {
            n += bufferSize - p.bufFree.getMark();
            n += p.incoming.getMark();
            n += p.toSatellite.getBuffer() + p.loss.getBuffer();
        }
        for (D3Net.Satellite s : net.satellites) {
            n += s.line.getMark() + s.transmit.getBuffer();
        }
        return n;
    }
}
