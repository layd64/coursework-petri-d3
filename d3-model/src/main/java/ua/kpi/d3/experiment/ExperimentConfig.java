package ua.kpi.d3.experiment;

public final class ExperimentConfig {
    public boolean quick = false;

    public double beta = 0.95;

    public int transientRuns = 5;
    public double transientHorizon = 1_200_000;
    public double transientStep = 1_000;

    public double transientRelEps = 0.05;

    public int pilotRuns = 20;

    public double relEps = 0.02;
    public int minReplications = 20;

    public int maxReplications = Integer.MAX_VALUE;

    public double warmup = 10_000;
    public double observation = 300_000;

    public double callIntervalHorizon = 3_600_000;

    public int normalityRuns = 100;
    public int normalityBins = 10;

    public int[] steadyBufferSizes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    public int steadyReps = 20;
    public double steadyObservation = 3_600_000;
    public double steadyWarmup = 60_000;
    public int[] anovaLevels = {2, 3, 4, 5};

    public double[] lossFreeHorizons = {60_000, 600_000, 3_600_000};
    public int lossFreeReps = 60;
    public int[] bufferGrid = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 50, 60, 70, 80, 100,
            120, 150, 180, 200, 250, 300, 400, 500};

    public double[] proposalChannelTimes = {10.0, 9.5, 9.0};
    public double proposalHorizon = 3_600_000;

    public int speedWarmupRuns = 40;
    public double speedWarmupTime = 100_000;
    public double[] speedHorizons = {100_000, 1_000_000, 3_600_000};
    public int speedReps = 10;

    public int[] factN = {2, 3};
    public double[] factChannel = {9.8, 10.0};
    public double[] factSat = {8.0, 12.0};
    public int factorialReps = 20;
    public int factorialPilot = 10;

    public static ExperimentConfig full() {
        return new ExperimentConfig();
    }

    public static ExperimentConfig quick() {
        ExperimentConfig c = new ExperimentConfig();
        c.quick = true;
        c.transientHorizon = 200_000;
        c.pilotRuns = 10;
        c.minReplications = 10;
        c.maxReplications = 20;
        c.steadyReps = 6;
        c.steadyObservation = 600_000;
        c.steadyWarmup = 30_000;
        c.lossFreeHorizons = new double[]{60_000, 600_000};
        c.lossFreeReps = 20;
        c.proposalHorizon = 300_000;
        c.observation = 100_000;
        c.speedWarmupRuns = 20;
        c.speedHorizons = new double[]{100_000, 600_000};
        c.speedReps = 3;
        c.factorialReps = 10;
        c.factorialPilot = 5;
        return c;
    }
}
