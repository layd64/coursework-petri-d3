package ua.kpi.d3.sim;

public interface SimulationObserver {
    void onEvent(double time, String eventName);

    default void onFinish(double endTime) {
    }
}
