package ua.kpi.d3.sim;

import com.stetsenko.petriobjlib.PetriObjModel;
import com.stetsenko.petriobjlib.PetriSim;
import ua.kpi.d3.model.D3Net;
import ua.kpi.d3.model.D3NetFactory;
import ua.kpi.d3.model.ModelParams;

import java.util.ArrayList;

public final class D3Simulation {
    private final D3Net net;
    private final ObservedPetriSim sim;
    private final PetriObjModel model;
    private boolean libraryProtocol;
    private boolean finished;

    public D3Simulation(ModelParams params) {
        this.net = D3NetFactory.create(params);
        synchronized (D3NetFactory.LOCK) {
            this.sim = new ObservedPetriSim(net.net);
            // у списку один об'єкт - вся мережа
            ArrayList<PetriSim> objects = new ArrayList<>();
            objects.add(sim);
            this.model = new PetriObjModel(objects);
        }
    }

    public D3Net net() {
        return net;
    }

    public D3Simulation addObserver(SimulationObserver observer) {
        sim.addObserver(observer);
        return this;
    }

    public D3Simulation withLibraryProtocol(boolean on) {
        this.libraryProtocol = on;
        return this;
    }

    public SimulationResult run(double simulationTime, double warmup) {
        if (finished) {
            throw new IllegalStateException("D3Simulation виконує лише один прогін; створіть новий об'єкт");
        }
        finished = true;
        StatisticsCollector stats = new StatisticsCollector(net, warmup);
        sim.addObserver(stats);
        model.setIsProtokol(libraryProtocol);
        model.setIsStatistics(true);
        model.go(simulationTime);  // імітація бібліотекою
        sim.finish(simulationTime);
        return stats.result();
    }

    public static SimulationResult run(ModelParams params, double simulationTime, double warmup) {
        return new D3Simulation(params).run(simulationTime, warmup);
    }
}
