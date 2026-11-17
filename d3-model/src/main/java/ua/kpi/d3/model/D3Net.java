package ua.kpi.d3.model;

import com.stetsenko.petriobjlib.PetriNet;
import com.stetsenko.petriobjlib.PetriP;
import com.stetsenko.petriobjlib.PetriT;

public final class D3Net {
    public static final class Satellite {
        public final String name;

        public final PetriP free;

        public final PetriP line;

        public final PetriP delivered;

        public final PetriT transmit;

        Satellite(String name, PetriP free, PetriP line, PetriP delivered, PetriT transmit) {
            this.name = name;
            this.free = free;
            this.line = line;
            this.delivered = delivered;
            this.transmit = transmit;
        }
    }

    public static final class Point {
        public final String name;

        public final PetriP gen;

        public final PetriP incoming;

        public final PetriP bufFree;

        public final PetriP queue;

        public final PetriP chFree;

        public final PetriP arrived;

        public final PetriP delivered;

        public final PetriP satCalls;

        public final PetriP lost;

        public final PetriT arrival;
        public final PetriT toBuffer;
        public final PetriT toSatellite;
        public final PetriT loss;
        public final PetriT transmit;

        public Satellite sat;

        Point(String name, PetriP gen, PetriP incoming, PetriP bufFree, PetriP queue, PetriP chFree,
              PetriP arrived, PetriP delivered, PetriP satCalls, PetriP lost,
              PetriT arrival, PetriT toBuffer, PetriT toSatellite, PetriT loss, PetriT transmit) {
            this.name = name;
            this.gen = gen;
            this.incoming = incoming;
            this.bufFree = bufFree;
            this.queue = queue;
            this.chFree = chFree;
            this.arrived = arrived;
            this.delivered = delivered;
            this.satCalls = satCalls;
            this.lost = lost;
            this.arrival = arrival;
            this.toBuffer = toBuffer;
            this.toSatellite = toSatellite;
            this.loss = loss;
            this.transmit = transmit;
        }
    }

    public final PetriNet net;
    public final ModelParams params;
    public final Point a;
    public final Point b;

    public final Satellite[] satellites;

    D3Net(PetriNet net, ModelParams params, Point a, Point b, Satellite[] satellites) {
        this.net = net;
        this.params = params;
        this.a = a;
        this.b = b;
        this.satellites = satellites;
    }

    public Point[] points() {
        return new Point[]{a, b};
    }

    public int satBusy() {
        int n = 0;
        for (Satellite s : satellites) {
            n += 1 - s.free.getMark();
        }
        return n;
    }

    public int satDelivered() {
        int n = 0;
        for (Satellite s : satellites) {
            n += s.delivered.getMark();
        }
        return n;
    }
}
