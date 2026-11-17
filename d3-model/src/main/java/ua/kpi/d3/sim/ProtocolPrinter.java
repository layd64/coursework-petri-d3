package ua.kpi.d3.sim;

import ua.kpi.d3.model.D3Net;

import java.io.PrintStream;
import java.util.Locale;

public final class ProtocolPrinter implements SimulationObserver {
    private static final String POINT_HEADER = " In Fr  Q Ch Tr  Ar  St  Ls";

    private final D3Net net;
    private final PrintStream out;
    private int lineNo;

    public ProtocolPrinter(D3Net net, PrintStream out) {
        this.net = net;
        this.out = out;
        printHeader();
    }

    private void printHeader() {
        out.println(String.format(Locale.US, "%3s %8s %-13s |%s |%s |%s",
                "№", "t, мс", "Подія", POINT_HEADER, POINT_HEADER, " Fr Ln Tr  Dv"));
        out.println(String.format(Locale.US, "%3s %8s %-13s |%-27s |%-27s |%-13s", "", "", "", " пункт А", " пункт В",
                " супутник"));
    }

    @Override
    public void onEvent(double time, String eventName) {
        lineNo++;
        out.println(String.format(Locale.US, "%3d %8.3f %-13s |%s |%s |%s",
                lineNo, time, eventName, point(net.a), point(net.b), sat()));
    }

    @Override
    public void onFinish(double endTime) {
        out.println(String.format(Locale.US, "%3s %8.3f %-13s |%s |%s |%s",
                "", endTime, "кінець", point(net.a), point(net.b), sat()));
    }

    private String point(D3Net.Point p) {
        return String.format(Locale.US, " %2d %2d %2d %2d %2d %3d %3d %3d",
                p.incoming.getMark(), p.bufFree.getMark(), p.queue.getMark(), p.chFree.getMark(),
                p.transmit.getBuffer(), p.arrived.getMark(), p.satCalls.getMark(), p.lost.getMark());
    }

    private String sat() {
        StringBuilder sb = new StringBuilder();
        for (D3Net.Satellite s : net.satellites) {
            sb.append(String.format(Locale.US, " %2d %2d %2d %3d",
                    s.free.getMark(), s.line.getMark(), s.transmit.getBuffer(), s.delivered.getMark()));
        }
        return sb.toString();
    }
}
