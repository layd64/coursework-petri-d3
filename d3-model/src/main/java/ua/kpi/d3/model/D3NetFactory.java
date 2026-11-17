package ua.kpi.d3.model;

import com.stetsenko.petriobjlib.ArcIn;
import com.stetsenko.petriobjlib.ArcOut;
import com.stetsenko.petriobjlib.ExceptionInvalidTimeDelay;
import com.stetsenko.petriobjlib.PetriNet;
import com.stetsenko.petriobjlib.PetriP;
import com.stetsenko.petriobjlib.PetriT;

import java.util.ArrayList;

public final class D3NetFactory {
    // бібліотека нумерує елементи статичними лічильниками, тому мережі будуються по черзі
    public static final Object LOCK = new Object();

    public static final int PRIORITY_TO_BUFFER = 2;
    public static final int PRIORITY_TO_SATELLITE = 1;
    public static final int PRIORITY_LOSS = 0;

    private D3NetFactory() {
    }

    public static D3Net create(ModelParams p) {
        synchronized (LOCK) {
            PetriP.initNext();
            PetriT.initNext();
            ArcIn.initNext();
            ArcOut.initNext();

            ArrayList<PetriP> places = new ArrayList<>();
            ArrayList<PetriT> transitions = new ArrayList<>();
            ArrayList<ArcIn> arcsIn = new ArrayList<>();
            ArrayList<ArcOut> arcsOut = new ArrayList<>();

            PlaceSet pa = new PlaceSet("A", p.bufferSize, places);
            PlaceSet pb = new PlaceSet("B", p.bufferSize, places);
            SatPlaces spA = new SatPlaces(p.duplexSatellite ? "SatA" : "Sat", places);
            SatPlaces spB = p.duplexSatellite ? new SatPlaces("SatB", places) : spA;

            D3Net.Point a = buildPoint(pa, p, spA, transitions, arcsIn, arcsOut);
            D3Net.Point b = buildPoint(pb, p, spB, transitions, arcsIn, arcsOut);
            D3Net.Satellite satA = buildSatellite(spA, p, transitions, arcsIn, arcsOut);
            D3Net.Satellite satB = p.duplexSatellite ? buildSatellite(spB, p, transitions, arcsIn, arcsOut) : satA;
            a.sat = satA;
            b.sat = satB;

            PetriNet net;
            try {
                net = new PetriNet("D3_DataTransfer", places, transitions, arcsIn, arcsOut);
            } catch (ExceptionInvalidTimeDelay e) {
                throw new IllegalStateException("Некоректна структура мережі Петрі: " + e.getMessage(), e);
            }
            D3Net.Satellite[] sats = p.duplexSatellite ? new D3Net.Satellite[]{satA, satB} : new D3Net.Satellite[]{satA};
            return new D3Net(net, p, a, b, sats);
        }
    }

    private static final class PlaceSet {
        final String name;
        final PetriP gen, incoming, bufFree, queue, chFree, arrived, delivered, satCalls, lost;

        PlaceSet(String x, int bufferSize, ArrayList<PetriP> places) {
            name = x;
            gen = add(places, new PetriP(x + ".Gen", 1));
            incoming = add(places, new PetriP(x + ".Incoming", 0));
            bufFree = add(places, new PetriP(x + ".BufFree", bufferSize));
            queue = add(places, new PetriP(x + ".Queue", 0));
            chFree = add(places, new PetriP(x + ".ChFree", 1));
            arrived = add(places, new PetriP(x + ".Arrived", 0));
            delivered = add(places, new PetriP(x + ".Delivered", 0));
            satCalls = add(places, new PetriP(x + ".SatCalls", 0));
            lost = add(places, new PetriP(x + ".Lost", 0));
        }
    }

    private static final class SatPlaces {
        final String name;
        final PetriP free, line, delivered;

        SatPlaces(String s, ArrayList<PetriP> places) {
            name = s;
            free = add(places, new PetriP(s + ".Free", 1));
            line = add(places, new PetriP(s + ".Line", 0));
            delivered = add(places, new PetriP(s + ".Delivered", 0));
        }
    }

    private static D3Net.Satellite buildSatellite(SatPlaces sp, ModelParams p, ArrayList<PetriT> transitions,
                                                  ArrayList<ArcIn> arcsIn, ArrayList<ArcOut> arcsOut) {
        PetriT transmit = add(transitions, new PetriT(sp.name + ".Transmit", p.satMean));
        if (p.satDev > 0) {
            transmit.setDistribution("unif", p.satMean);
            transmit.setParamDeviation(p.satDev);
        }
        arcsIn.add(new ArcIn(sp.line, transmit, 1));
        arcsOut.add(new ArcOut(transmit, sp.free, 1));
        arcsOut.add(new ArcOut(transmit, sp.delivered, 1));
        return new D3Net.Satellite(sp.name, sp.free, sp.line, sp.delivered, transmit);
    }

    private static D3Net.Point buildPoint(PlaceSet ps, ModelParams p, SatPlaces sat,
                                          ArrayList<PetriT> transitions,
                                          ArrayList<ArcIn> arcsIn, ArrayList<ArcOut> arcsOut) {
        String x = ps.name;

        // надходження пакета від абонентів
        PetriT arrival = add(transitions, new PetriT(x + ".Arrival", p.arrivalMean));
        if (p.arrivalDev > 0) {
            arrival.setDistribution("unif", p.arrivalMean);
            arrival.setParamDeviation(p.arrivalDev);
        }
        arcsIn.add(new ArcIn(ps.gen, arrival, 1));
        arcsOut.add(new ArcOut(arrival, ps.gen, 1));
        arcsOut.add(new ArcOut(arrival, ps.incoming, 1));
        arcsOut.add(new ArcOut(arrival, ps.arrived, 1));

        // запис у буфер, найвищий пріоритет
        PetriT toBuffer = add(transitions, new PetriT(x + ".ToBuffer", 0.0, PRIORITY_TO_BUFFER));
        arcsIn.add(new ArcIn(ps.incoming, toBuffer, 1));
        arcsIn.add(new ArcIn(ps.bufFree, toBuffer, 1));
        arcsOut.add(new ArcOut(toBuffer, ps.queue, 1));

        // буфер заповнений, виклик супутника
        PetriT toSatellite = add(transitions, new PetriT(x + ".ToSatellite", 0.0, PRIORITY_TO_SATELLITE));
        arcsIn.add(new ArcIn(ps.incoming, toSatellite, 1));
        arcsIn.add(new ArcIn(sat.free, toSatellite, 1));
        arcsOut.add(new ArcOut(toSatellite, sat.line, 1));
        arcsOut.add(new ArcOut(toSatellite, ps.satCalls, 1));

        // буфер заповнений і супутник зайнятий, пакет втрачено
        PetriT loss = add(transitions, new PetriT(x + ".Loss", 0.0, PRIORITY_LOSS));
        arcsIn.add(new ArcIn(ps.incoming, loss, 1));
        arcsOut.add(new ArcOut(loss, ps.lost, 1));

        // передача каналом, місце в буфері звільняється після передачі
        PetriT transmit = add(transitions, new PetriT(x + ".Transmit", p.channelTime));
        arcsIn.add(new ArcIn(ps.queue, transmit, 1));
        arcsIn.add(new ArcIn(ps.chFree, transmit, 1));
        arcsOut.add(new ArcOut(transmit, ps.chFree, 1));
        arcsOut.add(new ArcOut(transmit, ps.bufFree, 1));
        arcsOut.add(new ArcOut(transmit, ps.delivered, 1));

        return new D3Net.Point(x, ps.gen, ps.incoming, ps.bufFree, ps.queue, ps.chFree,
                ps.arrived, ps.delivered, ps.satCalls, ps.lost,
                arrival, toBuffer, toSatellite, loss, transmit);
    }

    private static <T> T add(ArrayList<? super T> list, T element) {
        list.add(element);
        return element;
    }
}
