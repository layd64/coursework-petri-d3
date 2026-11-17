package ua.kpi.d3.sim;

import com.stetsenko.petriobjlib.PetriNet;
import com.stetsenko.petriobjlib.PetriSim;
import com.stetsenko.petriobjlib.PetriT;

import java.util.ArrayList;
import java.util.List;

public class ObservedPetriSim extends PetriSim {
    private static final long serialVersionUID = 1L;

    private final transient List<SimulationObserver> observers = new ArrayList<>();

    public ObservedPetriSim(PetriNet net) {
        super(net);
    }

    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }

    // бібліотека викликає doT() на кожній події
    @Override
    public void doT() {
        if (observers.isEmpty()) {
            return;
        }

        PetriT event = eventMin;
        String eventName = event == null ? "-" : event.getName();
        double time = getCurrentTime();
        for (SimulationObserver o : observers) {
            o.onEvent(time, eventName);
        }
    }

    void finish(double endTime) {
        for (SimulationObserver o : observers) {
            o.onFinish(endTime);
        }
    }
}
