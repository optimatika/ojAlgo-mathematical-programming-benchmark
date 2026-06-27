# Netlib LP Benchmark: Run & Analyse

Reference: See `ojAlgo/.github/copilot-instructions.md` for project coding standards.

## Current status (2026-03-18)

**Run 1** (before: `develop` March 17, after: `ftran-btran` March 18) — noise 11.8%, no ORTools outliers. Result: no measurable LP improvement from ftran-btran optimisations. See `ojAlgo/plan-invertiblefactor-performance.prompt.md` Phase 4 for analysis.

**Run 2** (in progress) — confirmation benchmark to validate the Run 1 findings before proceeding with `plan-simplex-performance.prompt.md`. Record exact commit hashes with results.

## Quick start

```bash
# 1. Run the benchmark (from this project root)
#    Edit NetlibBenchmark.java first — see "Reducing noise" below.
mvn compile exec:java -Dexec.mainClass=org.ojalgo.benchmark.linear.netlib.NetlibBenchmark

# 2. Analyse results
python3 scripts/analyse_benchmark.py [before.csv] [after.csv] [reference_solver]
# Defaults: src/main/resources/before_benchmark_output.csv / after_benchmark_output.csv, ORTools
```

## Reducing noise

The previous run used `Parallelism.FOUR` (4 solvers in parallel on a laptop) and
produced ~39% max ORTools deviation — too high for confident classification.

### Recommended approach: one solver at a time

Edit `NetlibBenchmark.java` to benchmark one solver per run:

```java
// Run 1: reference only
static final String[] SOLVERS = { Contender.ORTOOLS };

// Run 2: solver under test
static final String[] SOLVERS = { Contender.OJALGO_LP_DUAL_SPARSE };

// Run 3: other solver (optional)
static final String[] SOLVERS = { Contender.OJALGO_LP_DUAL_DENSE };
```

Also reduce parallelism to `ONE`:

```java
configuration.parallelism = Parallelism.ONE;
```

### Other noise-reduction tips

- Close other applications (browser, IDE builds, etc.) during the run.
- Run on mains power, not battery.
- Disable Turbo Boost / energy-saving modes if possible.
- Run the reference solver (ORTools) in both the before and after batches so that
  noise can be estimated per batch.
- For very short-running models (< 50 ms), consider increasing the timeout or
  running multiple warm-up iterations if the harness supports it. Small absolute
  times are inherently noisier in relative terms.

### Target noise level

Aim for ORTools max deviation < 10–15%. At that level, improvements of ~20%+
in the ojAlgo solvers become clearly distinguishable from noise.

## File layout

| File | Purpose |
|------|---------|
| `src/main/resources/before_benchmark_output.csv` | Baseline benchmark CSV |
| `src/main/resources/after_benchmark_output.csv` | Post-change benchmark CSV |
| `src/main/resources/before_console.log` | Baseline console log (iterations, failures) |
| `src/main/resources/after_console.log` | Post-change console log |
| `scripts/analyse_benchmark.py` | Analysis script (this doc describes its use) |

## CSV format

Tab-separated, columns: `Model  Solver  Time  nbVars  nbExpr  density`

- `Time` is in nanoseconds. Empty = timeout/failure.
- Models come from the Netlib LP test set (`.SIF` files).
- Size filter: `1000 <= nbVars <= 10000` and `nbExpr <= 10000`.

## Analysis script details

`scripts/analyse_benchmark.py` does the following:

1. Parses both CSV files.
2. Computes the max absolute ratio deviation for the reference solver (default:
   ORTools) — this is the **noise threshold**.
3. For each non-reference solver, classifies every model as:
   - **IMPROVED** — faster by more than the noise threshold
   - **REGRESSED** — slower by more than the noise threshold
   - **Within noise** — change within the noise band
   - **Both timeout** — timed out in both runs
   - **NEW TIMEOUT** — solved before, times out now
   - **NEW SOLVE** — timed out before, solves now

Usage:
```bash
python3 scripts/analyse_benchmark.py                          # uses defaults
python3 scripts/analyse_benchmark.py before.csv after.csv     # custom files
python3 scripts/analyse_benchmark.py b.csv a.csv HiGHS        # custom reference solver
```

## Configuration reference (NetlibBenchmark.java)

| Field | Default | Notes |
|-------|---------|-------|
| `SOLVERS` | `{ORTOOLS, OJALGO_LP_DUAL_SPARSE, OJALGO_LP_DUAL_DENSE}` | Solvers to benchmark |
| `MIN_NB_VARS` | 1000 | Minimum variables to include a model |
| `MAX_NB_VARS` | 10000 | Maximum variables/constraints |
| `configuration.parallelism` | `Parallelism.FOUR` | Concurrent solver processes |
| `configuration.maxWaitTime` | 5 min (ms) | Per-model timeout |
| `configuration.refeenceSolver` | `Contender.ORTOOLS` | Reference for correctness checking |
