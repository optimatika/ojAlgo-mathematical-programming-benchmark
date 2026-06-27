#!/usr/bin/env python3
"""
Compare before/after LP benchmark CSV files.

Uses the ORTools (native, stable) solver as a noise baseline.
Changes in ojAlgo solvers are classified as improved, regressed,
or within noise based on the maximum observed ORTools deviation.

Usage:
    python3 scripts/analyse_benchmark.py [before.csv] [after.csv]

Defaults to src/main/resources/before_benchmark_output.csv and
src/main/resources/after_benchmark_output.csv when no arguments given.

CSV format (tab-separated):
    Model  Solver  Time  nbVars  nbExpr  density

Empty Time fields indicate timeout/failure (treated as missing).
"""

import csv
import os
import statistics
import sys


def parse_csv(path):
    """Parse a benchmark CSV into {model: {solver: time_ns}}."""
    data = {}
    with open(path) as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            model = row["Model"]
            solver = row["Solver"]
            time_str = row["Time"].strip()
            data.setdefault(model, {})[solver] = int(time_str) if time_str else None
    return data


def compute_noise(before, after, ref_solver, models):
    """Compute max absolute ratio deviation from 1.0 for the reference solver."""
    ratios = []
    for m in models:
        b = before.get(m, {}).get(ref_solver)
        a = after.get(m, {}).get(ref_solver)
        if b and a:
            ratios.append(a / b)
    if not ratios:
        print("WARNING: no reference solver data — cannot compute noise")
        return 0.5, []
    return max(abs(r - 1.0) for r in ratios), ratios


def classify(before, after, solver, models, noise):
    """Classify each model as improved/regressed/noise/timeout."""
    improved, regressed, neutral = [], [], []
    tboth, tnew, rnew = [], [], []

    for m in models:
        b = before.get(m, {}).get(solver)
        a = after.get(m, {}).get(solver)
        if b is None and a is None:
            tboth.append(m)
        elif b is None and a is not None:
            rnew.append((m, a))
        elif b is not None and a is None:
            tnew.append((m, b))
        else:
            pct = (a / b - 1) * 100
            threshold = noise * 100
            if pct < -threshold:
                improved.append((m, pct, b, a))
            elif pct > threshold:
                regressed.append((m, pct, b, a))
            else:
                neutral.append((m, pct))

    return improved, regressed, neutral, tboth, tnew, rnew


def ns_to_ms(ns):
    return ns / 1e6


def print_ref_analysis(before, after, ref_solver, models, noise, ratios):
    print("Reference solver: %s" % ref_solver)
    print("  Noise (max deviation from 1.0): %.1f%%" % (noise * 100))
    print(
        "  Ratio stats: median=%.3f, mean=%.3f, stdev=%.3f, min=%.3f, max=%.3f"
        % (
            statistics.median(ratios),
            statistics.mean(ratios),
            statistics.stdev(ratios),
            min(ratios),
            max(ratios),
        )
    )
    print("")
    print("  %s outliers (>20%% change):" % ref_solver)
    any_outlier = False
    for m in models:
        b = before.get(m, {}).get(ref_solver)
        a = after.get(m, {}).get(ref_solver)
        if b and a:
            pct = (a / b - 1) * 100
            if abs(pct) > 20:
                any_outlier = True
                print("    %-14s %+6.1f%%  (%8.1fms -> %8.1fms)" % (m, pct, ns_to_ms(b), ns_to_ms(a)))
    if not any_outlier:
        print("    (none)")


def print_solver_analysis(solver, improved, regressed, neutral, tboth, tnew, rnew):
    print("")
    print("=" * 80)
    print(solver)
    print("=" * 80)

    print("")
    print("IMPROVED beyond noise (%d):" % len(improved))
    for m, pct, b, a in sorted(improved, key=lambda x: x[1]):
        print("  %-14s %+6.1f%%  (%10.1fms -> %10.1fms)" % (m, pct, ns_to_ms(b), ns_to_ms(a)))

    print("")
    print("REGRESSED beyond noise (%d):" % len(regressed))
    for m, pct, b, a in sorted(regressed, key=lambda x: x[1], reverse=True):
        print("  %-14s %+6.1f%%  (%10.1fms -> %10.1fms)" % (m, pct, ns_to_ms(b), ns_to_ms(a)))

    print("")
    print("Within noise (%d):" % len(neutral))
    for m, pct in neutral:
        print("  %-14s %+6.1f%%" % (m, pct))

    print("")
    print("Both timeout (%d): %s" % (len(tboth), ", ".join(tboth)))

    if tnew:
        print("")
        print("NEW TIMEOUT (%d):" % len(tnew))
        for m, b in tnew:
            print("  %s (was %.1fms)" % (m, ns_to_ms(b)))

    if rnew:
        print("")
        print("NEW SOLVE (%d):" % len(rnew))
        for m, a in rnew:
            print("  %s (now %.1fms)" % (m, ns_to_ms(a)))


def main():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    if len(sys.argv) >= 3:
        before_path = sys.argv[1]
        after_path = sys.argv[2]
    else:
        before_path = os.path.join(base, "src/main/resources/before_benchmark_output.csv")
        after_path = os.path.join(base, "src/main/resources/after_benchmark_output.csv")

    ref_solver = sys.argv[3] if len(sys.argv) >= 4 else "ORTools"

    print("Before: %s" % before_path)
    print("After:  %s" % after_path)
    print("")

    before = parse_csv(before_path)
    after = parse_csv(after_path)
    models = sorted(set(before) | set(after))

    # Determine all solvers (excluding reference)
    all_solvers = set()
    for d in list(before.values()) + list(after.values()):
        all_solvers.update(d.keys())
    all_solvers.discard(ref_solver)
    solvers = sorted(all_solvers)

    # Noise from reference solver
    noise, ratios = compute_noise(before, after, ref_solver, models)
    print_ref_analysis(before, after, ref_solver, models, noise, ratios)

    # Analyse each solver
    for solver in solvers:
        improved, regressed, neutral, tboth, tnew, rnew = classify(before, after, solver, models, noise)
        print_solver_analysis(solver, improved, regressed, neutral, tboth, tnew, rnew)

    print("")


if __name__ == "__main__":
    main()
