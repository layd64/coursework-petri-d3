package ua.kpi.d3.sim;

import ua.kpi.d3.model.D3Net;

import java.util.ArrayList;
import java.util.List;

public final class TimeSeriesRecorder implements SimulationObserver {
    public static final class Point {
        public final double time;
        public final double satUtilization;
        public final double satCallFrequency;
        public final double lossProbability;
        public final double meanBufferOccupancy;

        Point(double time, double satUtilization, double satCallFrequency, double lossProbability,
              double meanBufferOccupancy) {
            this.time = time;
            this.satUtilization = satUtilization;
            this.satCallFrequency = satCallFrequency;
            this.lossProbability = lossProbability;
            this.meanBufferOccupancy = meanBufferOccupancy;
        }
    }

    private final D3Net net;
    private final double step;
    private final List<Point> points = new ArrayList<>();
    private double nextCheckpoint;
    private double lastTime;
    private double intSatBusy;
    private double intBufOcc;

    public TimeSeriesRecorder(D3Net net, double step) {
        this.net = net;
        this.step = step;
        this.nextCheckpoint = step;
    }

    @Override
    public void onEvent(double time, String eventName) {
        advance(time);
    }

    @Override
    public void onFinish(double endTime) {
        advance(endTime);
    }

    private void advance(double time) {
        double satBusy = (double) net.satBusy() / net.satellites.length;
        int bufOcc = 2 * net.params.bufferSize - net.a.bufFree.getMark() - net.b.bufFree.getMark();

        while (nextCheckpoint <= time) {
            double dt = nextCheckpoint - lastTime;
            intSatBusy += satBusy * dt;
            intBufOcc += bufOcc * dt;
            lastTime = nextCheckpoint;
            record(nextCheckpoint);
            nextCheckpoint += step;
        }
        double dt = time - lastTime;
        if (dt > 0) {
            intSatBusy += satBusy * dt;
            intBufOcc += bufOcc * dt;
            lastTime = time;
        }
    }

    private void record(double t) {
        int arrived = net.a.arrived.getMark() + net.b.arrived.getMark();
        int calls = net.a.satCalls.getMark() + net.b.satCalls.getMark()
                + net.a.lost.getMark() + net.b.lost.getMark();
        int lost = net.a.lost.getMark() + net.b.lost.getMark();
        points.add(new Point(t, intSatBusy / t, calls / t * 1000.0,
                arrived == 0 ? 0.0 : (double) lost / arrived, intBufOcc / t / 2.0));
    }

    public List<Point> points() {
        return points;
    }
}
