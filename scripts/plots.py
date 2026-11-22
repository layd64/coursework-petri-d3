#!/usr/bin/env python3
# графіки результатів для записки: python3 scripts/plots.py [results]
import csv
import glob
import math
import os
import re
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import FancyArrowPatch, Rectangle  # noqa: E402

RES = sys.argv[1] if len(sys.argv) > 1 else "results"
OUT = os.path.join(RES, "plots")
os.makedirs(OUT, exist_ok=True)

plt.rcParams.update({"figure.dpi": 110, "savefig.dpi": 160, "font.size": 10,
                     "axes.grid": True, "grid.alpha": 0.3})


def read(name):
    path = os.path.join(RES, name)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        rows = list(csv.reader(f, delimiter=";"))
    header, data = rows[0], rows[1:]
    return {h: [r[i] for r in data] for i, h in enumerate(header)}


def num(col):
    out = []
    for v in col:
        try:
            out.append(float(v))
        except ValueError:
            out.append(float("nan"))
    return out


def save(fig, name):
    fig.tight_layout()
    fig.savefig(os.path.join(OUT, name))
    plt.close(fig)
    print("  ", os.path.join(OUT, name))


# перехідний період
def plot_transient():
    d = read("transient.csv")
    if d is None:
        return
    t = num(d["time_s"])
    runs = len([k for k in d if re.fullmatch(r"U\d+", k)])
    specs = [("U", "Завантаження супутникової лінії", "Uсуп"),
             ("F", "Частота викликів супутникової лінії", "f, 1/с"),
             ("Occ", "Середнє заповнення буфера", "пакетів")]
    fig, axes = plt.subplots(3, 1, figsize=(9, 10), sharex=True)
    for ax, (key, title, ylab) in zip(axes, specs):
        for i in range(1, runs + 1):
            ax.plot(t, num(d[f"{key}{i}"]), lw=1, label=f"прогін {i}")
        ax.set_title(title + " (середнє на інтервалі [0; t])")
        ax.set_ylabel(ylab)
    axes[0].legend(ncol=runs, fontsize=8)
    axes[-1].set_xlabel("Модельний час t, с")
    save(fig, "transient.png")


# стаціонарні характеристики
def plot_steady():
    d = read("buffer_steady.csv")
    if d is None:
        return
    n = num(d["BufferSize"])
    f, fe = num(d["SatCallFreq_mean"]), num(d["SatCallFreq_eps"])
    u, ue = num(d["SatUtil_mean"]), num(d["SatUtil_eps"])
    p = num(d["LossProb_mean"])

    # гіперболічна апроксимація 1/f = b0 + b1 N (N >= 2)
    xs = [x for x, y in zip(n, f) if x >= 2 and y > 0]
    ys = [1 / y for x, y in zip(n, f) if x >= 2 and y > 0]
    m = len(xs)
    sx, sy = sum(xs), sum(ys)
    sxx = sum(x * x for x in xs)
    sxy = sum(x * y for x, y in zip(xs, ys))
    b1 = (m * sxy - sx * sy) / (m * sxx - sx * sx)
    b0 = (sy - b1 * sx) / m
    grid = [2 + i * 0.05 for i in range(int((max(n) - 2) / 0.05) + 1)]

    fig, axes = plt.subplots(1, 3, figsize=(15, 4.5))
    axes[0].errorbar(n, f, yerr=fe, fmt="o", capsize=3, label="імітація (середнє ± eps)")
    axes[0].plot(grid, [1 / (b0 + b1 * x) for x in grid], "--",
                 label=f"1/f = {b0:.3f} + {b1:.3f}·N")
    axes[0].set_yscale("log")
    axes[0].set_xlabel("Обсяг буфера N, пакетів")
    axes[0].set_ylabel("f, 1/с")
    axes[0].set_title("Частота викликів супутникової лінії")
    axes[0].legend(fontsize=8)

    axes[1].errorbar(n, u, yerr=ue, fmt="o", capsize=3, color="tab:green")
    axes[1].set_yscale("log")
    axes[1].set_xlabel("Обсяг буфера N, пакетів")
    axes[1].set_ylabel("Uсуп")
    axes[1].set_title("Завантаження супутникової лінії")

    pn = [(x, y) for x, y in zip(n, p) if y > 0]
    axes[2].plot([a for a, _ in pn], [b for _, b in pn], "o-", color="tab:red")
    axes[2].set_yscale("log")
    axes[2].set_xlabel("Обсяг буфера N, пакетів")
    axes[2].set_ylabel("Pвтр")
    axes[2].set_title("Ймовірність втрати пакета (стаціонарний режим)")
    for ax in axes:
        top = ax.secondary_xaxis("top", functions=(lambda x: x - 1, lambda x: x + 1))
        top.set_xticks([int(x) - 1 for x in n])
        top.set_xlabel("Місць для очікування K = N - 1", fontsize=9)
        ax.set_xticks([int(x) for x in n])
        ax.title.set_y(1.2)
    save(fig, "buffer_steady.png")


# робота без втрат
def plot_lossfree():
    files = sorted(glob.glob(os.path.join(RES, "buffer_lossfree_ch10.0_T*s.csv")),
                   key=lambda s: float(re.search(r"_T(\d+)s", s).group(1)))
    if not files:
        return
    fig, ax = plt.subplots(figsize=(9, 5))
    for path in files:
        horizon = float(re.search(r"_T(\d+)s", path).group(1))
        d = read(os.path.basename(path))
        nn, pp, runs = num(d["BufferSize"]), num(d["ZeroLossFraction"]), num(d["Runs"])
        err = [1.96 * math.sqrt(max(p * (1 - p), 0.25 / r) / r) for p, r in zip(pp, runs)]
        ax.errorbar(nn, pp, yerr=err, fmt="o-", capsize=3, label=f"Tроб = {horizon:.0f} с")
    ax.axhline(0.95, color="gray", ls="--", lw=1, label="P0 = 0.95")
    ax.set_xscale("log")
    ax.set_xlabel("Обсяг буфера N, пакетів (логарифмічна шкала)")
    ax.set_ylabel("P0 - частка прогонів без втрат")
    ax.set_title("Ймовірність роботи без втрат протягом Tроб (± 95% довірчий інтервал)")
    ax.legend()
    save(fig, "buffer_lossfree.png")

    d = read("buffer_required.csv")
    if d is None:
        return
    h = num(d["Horizon_s"])
    fig, ax = plt.subplots(figsize=(8, 5))
    ax.plot(h, num(d["RequiredBuffer_P095"]), "o-", label="N(0.95): P0 ≥ 0.95")
    ax.plot(h, num(d["RequiredBuffer_AllRuns"]), "s-", label="N(всі): втрат немає в жодному прогоні")
    ax.plot(h, num(d["UnlimitedMaxOccupancy_q95"]), "^--", label="необмежений буфер: 95%-квантиль макс. заповнення")
    hh = [h[0] * (h[-1] / h[0]) ** (i / 50) for i in range(51)]
    q = num(d["UnlimitedMaxOccupancy_q95"])
    c = q[-1] / math.sqrt(h[-1])
    ax.plot(hh, [c * math.sqrt(x) for x in hh], ":", color="gray", label="∝ √Tроб")
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_xlabel("Тривалість роботи Tроб, с")
    ax.set_ylabel("Обсяг буфера N, пакетів")
    ax.set_title("Необхідний обсяг буфера для роботи без втрат")
    ax.legend(fontsize=8)
    save(fig, "buffer_required.png")


# пропозиції
def plot_proposals():
    d = read("proposals.csv")
    if d is None:
        return
    ct = num(d["ChannelTime_ms"])
    labels = [f"{c:.1f} мс\n(ρ={c / 10:.2f})" for c in ct]
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))
    n95 = num(d["RequiredBuffer_P095"])
    nall = num(d["RequiredBuffer_AllRuns"])
    x = range(len(ct))
    axes[0].bar([i - 0.2 for i in x], n95, width=0.4, label="N(0.95)")
    axes[0].bar([i + 0.2 for i in x], nall, width=0.4, label="N(всі)")
    for i in x:
        axes[0].text(i - 0.2, n95[i], f"{n95[i]:.0f}", ha="center", va="bottom")
        axes[0].text(i + 0.2, nall[i], f"{nall[i]:.0f}", ha="center", va="bottom")
    axes[0].set_xticks(list(x), labels)
    axes[0].set_ylabel("Обсяг буфера, пакетів")
    axes[0].set_title("Необхідний обсяг буфера для роботи без втрат")
    axes[0].legend()
    axes[1].bar(list(x), num(d["SatCallFreq_N2"]), color="tab:orange")
    axes[1].set_xticks(list(x), labels)
    axes[1].set_ylabel("f, 1/с")
    axes[1].set_title("Частота викликів супутникової лінії при N = 2")
    for i, v in enumerate(num(d["SatCallFreq_N2"])):
        axes[1].text(i, v, f"{v:.3f}", ha="center", va="bottom")
    save(fig, "proposals.png")


# розподіл відгуків
def plot_baseline():
    d = read("baseline_runs.csv")
    if d is None:
        return
    fig, axes = plt.subplots(1, 2, figsize=(11, 4))
    axes[0].hist(num(d["SatCallFreq_per_s"]), bins=12, edgecolor="black")
    axes[0].set_xlabel("f, 1/с")
    axes[0].set_ylabel("Кількість прогонів")
    axes[0].set_title("Частота викликів супутникової лінії (N = 2)")
    axes[1].hist(num(d["SatUtilization"]), bins=12, edgecolor="black", color="tab:green")
    axes[1].set_xlabel("Uсуп")
    axes[1].set_title("Завантаження супутникової лінії (N = 2)")
    save(fig, "baseline_hist.png")


# закон розподілу оцінок f і U
def plot_estimates():
    d = read("estimates_hist.csv")
    if d is None:
        return
    titles = {"SatCallFreq": ("f, 1/с", "Частота викликів супутникової лінії"),
              "SatUtilization": ("Uсуп", "Завантаження супутникової лінії")}
    fig, axes = plt.subplots(1, 2, figsize=(11, 4))
    for ax, key in zip(axes, titles):
        idx = [i for i, c in enumerate(d["Characteristic"]) if c == key]
        lo = [float(d["BinLeft"][i]) for i in idx]
        hi = [float(d["BinRight"][i]) for i in idx]
        obs = [float(d["Observed"][i]) for i in idx]
        exp = [float(d["ExpectedNormal"][i]) for i in idx]
        mid = [(a + b) / 2 for a, b in zip(lo, hi)]
        ax.bar(lo, obs, width=[b - a for a, b in zip(lo, hi)], align="edge", edgecolor="black",
               color="tab:blue" if key == "SatCallFreq" else "tab:green", alpha=0.7, label="спостережені частоти")
        ax.plot(mid, exp, "o-", color="tab:red", label="нормальний закон")
        ax.set_xlabel(titles[key][0])
        ax.set_ylabel("Кількість прогонів")
        ax.set_title(titles[key][1] + " (100 прогонів)")
        ax.legend(fontsize=8)
    save(fig, "estimates_normal.png")


# верифікація затримок
def plot_delays():
    d = read("verification_delays.csv")
    if d is None:
        return
    names = list(dict.fromkeys(d["Transition"]))
    fig, axes = plt.subplots(1, len(names), figsize=(6 * len(names), 4))
    if len(names) == 1:
        axes = [axes]
    titles = {"A.Arrival": "Інтервал між надходженнями (unif(10;3))",
              "Sat.Transmit": "Час передачі супутником (unif(10;5))"}
    for ax, name in zip(axes, names):
        idx = [i for i, v in enumerate(d["Transition"]) if v == name]
        left = [float(d["BinLeft_ms"][i]) for i in idx]
        right = [float(d["BinRight_ms"][i]) for i in idx]
        obs = [float(d["Observed"][i]) for i in idx]
        exp = [float(d["Expected"][i]) for i in idx]
        ax.bar(left, obs, width=[r - l for l, r in zip(left, right)], align="edge", edgecolor="black",
               alpha=0.7, label="спостережені частоти")
        ax.step(left + [right[-1]], exp + [exp[-1]], where="post", color="tab:red", lw=2,
                label="теоретичні (рівномірний закон)")
        ax.set_xlabel("Затримка, мс")
        ax.set_ylabel("Кількість значень")
        ax.set_title(titles.get(name, name))
        ax.legend(fontsize=8, loc="lower center")
    save(fig, "verification_delays.png")


# швидкодія бібліотеки
def plot_speed():
    d = read("speed_comparison.csv")
    if d is None:
        return
    h = [x / 1000 for x in num(d["Horizon_ms"])]
    om, mm = num(d["Original_median_ms"]), num(d["Modified_median_ms"])
    oe, me = num(d["Original_events_per_s"]), num(d["Modified_events_per_s"])
    x = list(range(len(h)))
    labels = [f"T = {v:.0f} с" for v in h]
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5))
    axes[0].bar([i - 0.2 for i in x], om, width=0.4, label="оригінальна бібліотека")
    axes[0].bar([i + 0.2 for i in x], mm, width=0.4, label="модифікована бібліотека")
    for i in x:
        axes[0].text(i, max(om[i], mm[i]), f"×{om[i] / mm[i]:.2f}", ha="center", va="bottom", fontweight="bold")
    axes[0].set_xticks(x, labels)
    axes[0].set_yscale("log")
    axes[0].set_ylabel("Час одного прогону (медіана), мс")
    axes[0].set_title("Час імітації")
    axes[0].legend()
    axes[1].bar([i - 0.2 for i in x], [v / 1e6 for v in oe], width=0.4, label="оригінальна бібліотека")
    axes[1].bar([i + 0.2 for i in x], [v / 1e6 for v in me], width=0.4, label="модифікована бібліотека")
    axes[1].set_xticks(x, labels)
    axes[1].set_ylabel("Млн подій за секунду")
    axes[1].set_title("Продуктивність алгоритму імітації")
    axes[1].legend(loc="lower right")
    save(fig, "speed_comparison.png")


# інтервали між викликами
def plot_call_intervals():
    d = read("call_intervals_hist.csv")
    if d is None:
        return
    lo, hi = num(d["BinLeft_ms"]), num(d["BinRight_ms"])
    fig, ax = plt.subplots(figsize=(10, 4.8))
    ax.bar(lo, num(d["Observed"]), width=[h - l for l, h in zip(lo, hi)], align="edge", edgecolor="black",
           alpha=0.6, label="спостережені частоти")
    mid = [(l + h) / 2 for l, h in zip(lo, hi)]
    ax.plot(mid, num(d["ExpectedExponential"]), "o-", color="tab:red", ms=3, label="експоненціальний закон")
    ax.plot(mid, num(d["ExpectedGamma"]), "s-", color="tab:green", ms=3, label="гамма-закон (метод моментів)")
    if "ExpectedLognormal" in d:
        ax.plot(mid, num(d["ExpectedLognormal"]), "^-", color="tab:purple", ms=3, label="логнормальний закон")
    ax.set_xlabel("Інтервал між викликами супутникової лінії, мс")
    ax.set_ylabel("Кількість інтервалів")
    ax.set_title("Розподіл інтервалу між викликами супутникової лінії (N = 2, 1 год)")
    ax.legend()
    save(fig, "call_intervals.png")


# uml-діаграма класів
def draw_uml():
    fig, ax = plt.subplots(figsize=(22, 15))
    ax.axis("off")
    ax.set_xlim(0, 22.2)
    ax.set_ylim(0.1, 15)
    boxes = {}

    def cls(name, x, y, members, stereo=None, color="#ffffff", w=3.6):
        h = 0.62 + 0.27 * len(members) + (0.25 if stereo else 0)
        ax.add_patch(Rectangle((x, y - h), w, h, facecolor=color, edgecolor="black", lw=1.2, zorder=2))
        ty = y - 0.22
        if stereo:
            ax.text(x + w / 2, ty, stereo, ha="center", va="center", fontsize=7.5, style="italic", zorder=3)
            ty -= 0.25
        ax.text(x + w / 2, ty, name, ha="center", va="center", fontsize=9.5, fontweight="bold", zorder=3)
        ax.plot([x, x + w], [ty - 0.2, ty - 0.2], color="black", lw=0.8, zorder=3)
        for i, m in enumerate(members):
            ax.text(x + 0.08, ty - 0.42 - 0.27 * i, m, ha="left", va="center", fontsize=7.3, family="monospace",
                    zorder=3)
        boxes[name] = (x, y - h, w, h)

    def anchor(name, side, shift=0.0):
        x, y, w, h = boxes[name]
        return {"top": (x + w / 2 + shift, y + h), "bottom": (x + w / 2 + shift, y),
                "left": (x, y + h / 2 + shift), "right": (x + w, y + h / 2 + shift)}[side]

    def rel(a, sa, b, sb, kind="assoc", rad=0.0, sha=0.0, shb=0.0, label=None):
        head, ls, hollow = {"inherit": ("-|>", "-", True), "impl": ("-|>", "--", True),
                            "assoc": ("->", "-", False), "dep": ("->", "--", False)}[kind]
        pa, pb = anchor(a, sa, sha), anchor(b, sb, shb)
        ax.add_patch(FancyArrowPatch(pa, pb, arrowstyle=head, linestyle=ls, mutation_scale=16 if hollow else 11,
                                     lw=1.0, connectionstyle=f"arc3,rad={rad}", edgecolor="black",
                                     facecolor="white" if hollow else "black", zorder=1))
        if label:
            ax.text((pa[0] + pb[0]) / 2 + 0.1, (pa[1] + pb[1]) / 2, label, fontsize=7.5, style="italic",
                    color="#333333", zorder=4)

    lib = "#e8e8e8"
    col = [0.2, 4.2, 8.2, 12.2, 16.0, 19.7]
    # рядок 1: бібліотека
    ax.text(0.2, 14.75, "Бібліотека PetriObjLib", fontsize=11,
            fontweight="bold")
    cls("PetriP", col[0], 14.4, ["-mark: int", "+getMark() / getMean()"], color=lib)
    cls("PetriT", col[1], 14.4, ["-timeOut: List<Double>", "-priority, probability", "+actIn() / actOut()",
                                 "+setMoments(b)"], color=lib)
    cls("PetriNet", col[2], 14.4, ["+getListP(): PetriP[]", "+getListT(): PetriT[]"], color=lib)
    cls("PetriSim", col[3], 14.4, ["#eventMin: PetriT", "+input() / output()", "+findActiveT()/doConflikt()",
                                   "+doT()"], color=lib)
    cls("PetriObjModel", col[4], 14.4, ["+go(timeModeling)", "-goOneObject()  [D3]", "+setIsProtokol(b)"], color=lib)
    cls("ArcIn / ArcOut", col[5], 14.4, ["-k: int"], color=lib, w=2.3)

    # рядок 2: модель
    ax.text(0.2, 11.75, "Модель та імітація", fontsize=11, fontweight="bold")
    cls("ModelParams", col[0], 11.4, ["+arrivalMean, arrivalDev", "+channelTime, bufferSize", "+satMean, satDev",
                                      "+duplexSatellite", "+base() / with...()"])
    cls("D3NetFactory", col[1], 11.4, ["+LOCK: Object", "+create(p): D3Net", "-buildPoint(...)"])
    cls("D3Net", col[2], 11.4, ["+net: PetriNet", "+params: ModelParams", "+a, b: Point", "+satellites: Satellite[]"])
    cls("ObservedPetriSim", col[3], 11.4, ["-observers: List", "+doT()  (перевизначено)", "~finish(T)"])
    cls("D3Simulation", col[4], 11.4, ["-net, sim, model", "+addObserver(o)", "+run(T, warmup)"], w=3.3)
    cls("Runner", 19.75, 11.4, ["-pool: Executor", "+replicate(...)"], w=2.2)
    # рядок 3-4: спостерігачі та результат
    cls("SimulationObserver", col[3], 8.4, ["+onEvent(t, name)", "+onFinish(T)"], stereo="«interface»")
    cls("TimeSeriesRecorder", col[0], 6.2, ["+points(): List<Point>"])
    cls("ProtocolPrinter", col[1], 6.2, ["+onEvent(t, name)"])
    cls("InvariantChecker", col[2], 6.2, ["-check(t)", "+ok(): boolean"])
    cls("StatisticsCollector", col[3], 6.2, ["-integrate(t)", "+result()"])
    cls("SimulationResult", col[4], 6.2, ["+satUtilization", "+satCallFrequency()", "+lossProbability()",
                                          "+balanceHolds()"])

    # рядок 5: експерименти
    ax.text(0.2, 3.35, "Запуск та експерименти", fontsize=11, fontweight="bold")
    cls("Main", 0.2, 3.0, ["+main(args)", "команди, --lib"], w=2.6)
    cls("Experiments", 3.2, 3.0, ["+verification()", "+transientPeriod()", "+baseline(p)", "+bufferLossFree()"],
        w=3.1)
    cls("FactorialExperiment", 6.7, 3.0, ["+run()  // 2^3:", "Кохрен, Ст'юдент, Фішер"], w=3.4)
    cls("Stats", 10.5, 3.0, ["+chebyshevReplications()", "+anova(), regression()"], w=3.2)
    cls("SpeedComparison", 14.1, 3.0, ["+run()", "+benchChild(cfg)"], w=2.9)
    cls("LibraryVariant", 17.4, 3.0, ["+loaded()", "+relaunch(v, args)"], w=3.0)

    # зв'язки
    rel("D3NetFactory", "top", "PetriT", "bottom", "dep", label="створює")
    rel("D3NetFactory", "top", "PetriP", "bottom", "dep", sha=-0.8)
    rel("D3Net", "top", "PetriNet", "bottom", "assoc")
    rel("ObservedPetriSim", "top", "PetriSim", "bottom", "inherit", label="extends")
    rel("D3Simulation", "top", "PetriObjModel", "bottom", "assoc", label="go(T)")
    rel("ModelParams", "right", "D3NetFactory", "left", "dep")
    rel("D3NetFactory", "right", "D3Net", "left", "dep")
    rel("D3Simulation", "left", "ObservedPetriSim", "right", "assoc")
    rel("ObservedPetriSim", "bottom", "SimulationObserver", "top", "assoc", label="observers")
    for o in ("TimeSeriesRecorder", "ProtocolPrinter", "InvariantChecker", "StatisticsCollector"):
        rel(o, "top", "SimulationObserver", "bottom", "impl")
    rel("StatisticsCollector", "right", "SimulationResult", "left", "dep", sha=0.25, shb=0.5)
    rel("D3Simulation", "bottom", "SimulationResult", "top", "dep")
    rel("Runner", "left", "D3Simulation", "right", "dep")
    rel("Main", "right", "Experiments", "left", "dep")
    rel("Experiments", "right", "FactorialExperiment", "left", "dep")
    rel("FactorialExperiment", "right", "Stats", "left", "dep")
    rel("SpeedComparison", "right", "LibraryVariant", "left", "dep")
    def poly(points):
        xs, ys = zip(*points)
        ax.plot(xs[:-1] + (xs[-1],), ys, linestyle="--", color="black", lw=1.0, zorder=1)
        ax.add_patch(FancyArrowPatch(points[-2], points[-1], arrowstyle="->", mutation_scale=11, lw=1.0,
                                     linestyle="--", color="black", zorder=1))

    ex_x, ex_y, ex_w, _ = boxes["Experiments"]
    rn_x, rn_y, rn_w, rn_h = boxes["Runner"]
    poly([(ex_x + ex_w / 2, ex_y), (ex_x + ex_w / 2, 1.05), (22.1, 1.05), (22.1, rn_y + rn_h / 2),
          (rn_x + rn_w, rn_y + rn_h / 2)])
    mn_x, mn_y, mn_w, _ = boxes["Main"]
    lv_x, lv_y, lv_w, _ = boxes["LibraryVariant"]
    poly([(mn_x + mn_w / 2, mn_y), (mn_x + mn_w / 2, 0.8), (lv_x + lv_w / 2, 0.8), (lv_x + lv_w / 2, lv_y)])

    ax.text(0.2, 0.3, "—▷ наслідування    - -▷ реалізація інтерфейсу    → асоціація    - -> залежність    "
            "(сірим - класи бібліотеки; [D3] - метод модифікованої бібліотеки)", fontsize=9)
    save(fig, "uml_classes.png")

if __name__ == "__main__":
    print("Графіки:")
    plot_transient()
    plot_steady()
    plot_lossfree()
    plot_proposals()
    plot_baseline()
    plot_estimates()
    plot_delays()
    plot_speed()
    plot_call_intervals()
    draw_uml()
