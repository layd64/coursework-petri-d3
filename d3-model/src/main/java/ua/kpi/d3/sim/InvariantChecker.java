package ua.kpi.d3.sim;

import ua.kpi.d3.model.D3Net;

import java.util.ArrayList;
import java.util.List;

public final class InvariantChecker implements SimulationObserver {
    private final D3Net net;
    private final int n;
    private long checks;
    private long priorityChecks;
    private final List<String> violations = new ArrayList<>();

    public InvariantChecker(D3Net net) {
        this.net = net;
        this.n = net.params.bufferSize;
    }

    @Override
    public void onEvent(double time, String eventName) {
        check(time);
    }

    @Override
    public void onFinish(double endTime) {
        check(endTime);
    }

    private void check(double t) {
        checks++;
        for (var pl : net.net.getListP()) {
            if (pl.getMark() < 0) {
                violate(t, "від'ємне маркування позиції " + pl.getName());
            }
        }

        for (var tr : net.net.getListT()) {
            if (tr.getBuffer() > 1) {
                violate(t, "перехід " + tr.getName() + " має " + tr.getBuffer() + " активних каналів (очікувалось <= 1)");
            }
        }
        for (D3Net.Point p : net.points()) {
            String x = p.name;
            expect(t, x + ".Gen + [Arrival] = 1", p.gen.getMark() + p.arrival.getBuffer(), 1);
            expect(t, x + ".BufFree + Queue + [ToBuffer] + [Transmit] = N",
                    p.bufFree.getMark() + p.queue.getMark() + p.toBuffer.getBuffer() + p.transmit.getBuffer(), n);
            expect(t, x + ".ChFree + [Transmit] = 1", p.chFree.getMark() + p.transmit.getBuffer(), 1);
            int inPoint = p.incoming.getMark() + p.toBuffer.getBuffer() + p.queue.getMark() + p.transmit.getBuffer()
                    + p.delivered.getMark() + p.toSatellite.getBuffer() + p.satCalls.getMark()
                    + p.loss.getBuffer() + p.lost.getMark();
            expect(t, x + ": збереження пакетів", inPoint, p.arrived.getMark());

            if (p.toSatellite.getBuffer() > 0) {
                priorityChecks++;
                if (p.bufFree.getMark() > 0) {
                    violate(t, x + ".ToSatellite спрацював при вільному місці в буфері");
                }
            }
            if (p.loss.getBuffer() > 0) {
                priorityChecks++;
                if (p.bufFree.getMark() > 0 || p.sat.free.getMark() > 0) {
                    violate(t, x + ".Loss спрацював, хоча був вільний буфер або супутник");
                }
            }
        }

        for (D3Net.Satellite s : net.satellites) {
            int toSat = 0;
            int calls = 0;
            for (D3Net.Point p : net.points()) {
                if (p.sat == s) {
                    toSat += p.toSatellite.getBuffer();
                    calls += p.satCalls.getMark();
                }
            }
            expect(t, s.name + ".Free + [X.ToSat] + " + s.name + ".Line + [" + s.name + ".Transmit] = 1",
                    s.free.getMark() + toSat + s.line.getMark() + s.transmit.getBuffer(), 1);
            expect(t, s.name + ": збереження пакетів",
                    s.line.getMark() + s.transmit.getBuffer() + s.delivered.getMark(), calls);
        }
    }

    private void expect(double t, String what, int actual, int expected) {
        if (actual != expected) {
            violate(t, what + ": " + actual + " != " + expected);
        }
    }

    private void violate(double t, String msg) {
        if (violations.size() < 20) {
            violations.add(String.format(java.util.Locale.US, "t = %.4f: %s", t, msg));
        } else if (violations.size() == 20) {
            violations.add("...");
        }
    }

    public long checks() {
        return checks;
    }

    public long priorityChecks() {
        return priorityChecks;
    }

    public List<String> violations() {
        return violations;
    }

    public boolean ok() {
        return violations.isEmpty();
    }
}
