# TODO

Actions arising from the August 2026 MIPLIB "easy set" parallelism sweep. Raw results and console logs
are in `results/2026/08/`.

## Background

The sweep ran the 94-model set from ojAlgo's `MIPLIBTheEasySet` against ojAlgo-MIP, SCIP, HiGHS and SSC-LP
at four worker counts (8, 4, 2, 1 workers -- 3, 5, 9, 18 threads per worker respectively). Because
`Parallelism.THREADS.divideBy(workers)` couples the two, worker contention and per-solver thread budget
move together across the four runs.

Two configuration gates meant the native solvers ran single-threaded throughout: `SolverHiGHS` requires
`parallelism >= 4 && nbVars > 1_000` and no model in this set exceeds 1000 variables; `SolverSCIP`
requires `parallelism > 18`, unreachable on an 18-thread machine, and `isConcurrent()` returns false.
So HiGHS and SCIP were compared serial-to-serial in all four runs, and ojAlgo was the only solver whose
parallelism was actually exercised.

Headline results, 1 worker / 18 threads:

| Solver | Solved | Notes |
|---|---|---|
| HiGHS | 94/94 | |
| SCIP | 93/94 | fails `neos-2624317-amur` -- see item 3 |
| ojAlgo-MIP | 52/94 | 42 timeouts, 4 unstable, 1 out-of-memory |
| SSC-LP | 26/94 | |

SCIP is roughly 30% faster than HiGHS on the typical model (geometric mean of SCIP/HiGHS = 0.706--0.763,
consistent across all four runs). ojAlgo scales well with threads up to a knee around 9 -- 95% parallel
efficiency from 3 to 5 threads, 82% from 5 to 9, 61% from 9 to 18.

---

## 1. Capability-first benchmark mode -- DONE

The first outer iteration now always solves each pair exactly once, which by itself answers whether the
solver copes. Further iterations exist only to refine the timing and are bounded by
`Configuration.maxIterations` (default 20, previously a hard-coded 20 inside `ResultsSet.isStable`). Set it
to 1 for a pure capability test.

- `ForkedTask.execute` takes a `maxSolves` parameter; `doOnePair` passes 1 on the first iteration and 0
  (repeat within budget) afterwards, so the inner convergence loop no longer runs during the capability pass
- `ResultsSet` takes a max count, checked before the "times agree" test, so a max of 1 completes after one
  measurement
- A **Models Solved** tally is printed per solver after the results table -- the primary metric
- LP and QP benchmarks are unaffected: they keep the default and behave as before

Measured on r50x360/HiGHS: 17.1s for one pass against 50.5s for the repeat loop, reported times within 1.5%.

Two things to keep in mind when reading capability-mode output:

- The times are **cold** -- one solve in a fresh JVM, including class loading, JIT warm-up and native
  library loading. Observed 86.6ms vs 4.0ms for the same p0040/HiGHS pair across the two modes. On fast
  models that overhead dominates. Treat them as "it worked", not as measurements.
- Wall clock is dominated by failures: `(#failures x maxWaitTime) + sum(successful times)`. At the rates
  observed here that is 111 failing pairs, so a 5-minute timeout costs roughly 9-10 hours single-worker.
  Worker count is the lever that matters, not threads per worker -- `MIPLIBTheEasySet` is set to
  `Parallelism.FOUR`, which should bring it to around 2.5 hours. More workers slow every solve by ~10-20%,
  which can flip a model sitting right at the timeout, so keep the timeout generous.

## 1b. Report the first pass in every benchmark, not just capability runs

A pair only logs when it finishes, and the "Solved" line sits in the `isStable()` branch of
`doOnePair`. With `maxIterations = 1` that fires on the first measurement, so capability runs print
`Solved` for everything. With the default 20 it needs three measurements, so in a timing run **iteration 1
prints only the failures** - TIMEOUT, UNSTABLE and WRONG each log immediately, successes stay silent until
iteration 3. The first pass looks like nothing but bad news even when most pairs are fine.

Log the first measurement regardless of mode, then again when the timing settles:

```java
} else if (mainResults.count() == 1) {
    BasicLogger.debugColumns(WIDTH, model, solver, "Solved", ...);   // first pass, always
    if (mainResults.isStable()) { iterDone.add(modelSolverPair); }
} else if (mainResults.isStable()) {
    BasicLogger.debugColumns(WIDTH, model, solver, "Time stable", ...);
    iterDone.add(modelSolverPair);
}
```

Costs one extra line per pair in timing runs, and makes iteration 1 a real capability scan everywhere.

## 2. ojAlgo runs out of memory on opt1217

`java.lang.OutOfMemoryError: Java heap space` on a 769-variable, 65-constraint model with a 12GB heap.

```bash
java -cp <benchmark-classpath> ForkTest /optimisation/MIPLIB/opt1217.mps ojAlgo-MIP 18
```

Points at unbounded node retention in `IntegerSolver` rather than anything specific to the model. It is
thread-count sensitive: at 3 threads the wall clock runs out first and it reports TIMEOUT, at 5 threads
and above the heap goes first and it reports FAILED.

Related: `AbstractBenchmark.doOnePair` catches `Exception` and logs only the model/solver pair, discarding
`cause`. Add it -- currently an OOM and a null pointer produce identical output, which is why this went
unexamined across four runs.

## 3. Native solver build matrix -- what optimisation-service actually ships

**This is the large one.** `optimisation-service-server` depends heavily on HiGHS and SCIP, builds specific
versions from source, and packages them into the Docker image. Nothing in any benchmark run so far has
tested those builds. The benchmark has been measuring whatever happened to be installed on a MacBook, or
whatever OR-Tools bundled.

### What is actually in play

Three HiGHS versions and two distinct SCIP 10.0.3 builds:

| Source | HiGHS | SCIP | Notes |
|---|---|---|---|
| optimisation-service Dockerfile | **1.13.1** | **10.0.3** | built from source, the one that ships |
| Homebrew (benchmark machine) | 1.15.1 | 10.0.3 | what the August 2026 runs measured |
| OR-Tools 9.15.6755 | 1.12.0 | 10.0 and 9.2 bundled | what pre-lazy-fix runs measured |

The two SCIP 10.0.3 builds are the same upstream tag configured very differently. From the Dockerfile:

```
-DSHARED=on -DLPS=spx -DPAPILO=on -DSYM=snauty
-DZIMPL=off -DIPOPT=off -DEXPRINT=none
-DAMPL=off -DREADLINE=off -DLAPACK=off -DEXACTSOLVE=off
```

with SoPlex 8.0.3 and PaPILO 3.0.1 built from source alongside.

What each SCIP actually contains, from `nm` and `otool -L`:

| | OR-Tools bundled | Homebrew 10.0.3 | Docker (per the switches above) |
|---|---|---|---|
| SoPlex (static) | 3,506 symbols | 6,966 symbols | on, 8.0.3 |
| **PaPILO** | **absent** | **3,261 symbols** | **on, 3.0.1** |
| **nauty** | **absent** | **20 symbols** | **on (snauty)** |
| Ipopt / CppAD | not linked | linked | off |
| OpenBLAS / TBB / GMP / Boost | not linked | linked | off (LAPACK=off) |
| Transitive deps | libz, libc++, libSystem only | 20 dylibs | -- |

The Docker build is a **hybrid**: PaPILO and snauty like Homebrew, everything else stripped like OR-Tools.
Neither measured build predicts it, which is the argument for building it rather than reasoning about it.

HiGHS is a plain version difference, no comparable switches: 1.12.0 vs 1.13.1 vs 1.15.1.

### Measured 2026-08-13: Homebrew vs OR-Tools

Five models -- the four slowest of the easy set plus `neos-2624317-amur` -- one solve each, 10-minute
timeout, 2 workers, identical `Configurator` and identical ojAlgo integrations in both runs. Only the
loaded `.dylib` differed. Results in `src/main/resources/miplib_builds_ortools_output.csv`; the Homebrew
side is in the run log (its CSV was overwritten by a stray full-set run, kept as
`miplib_builds_homebrew_full94_output.csv`).

**SCIP -- the OR-Tools build wins on every model:**

| Model | Homebrew | OR-Tools | |
|---|---|---|---|
| neos-2624317-amur | **417.5s** | **32.2s** | 13x |
| graphdraw-gemcutter | 54.7s | 42.9s | 1.3x |
| mas76 | 26.3s | 20.8s | 1.3x |
| pk1 | 56.1s | 47.7s | 1.2x |
| prod2 | 67.8s | 61.1s | 1.1x |
| total | 622s | 205s | geo-mean 0.52x |

**HiGHS -- mixed, no winner:** 1.12.0 is faster on mas76 (0.69x) and graphdraw (0.79x), slower on prod2
(1.20x) and much slower on neos-2624317-amur (4.91x). Totals coincidentally identical at 397s.

Two corrections to earlier notes in this file:

- `neos-2624317-amur` is **not** a SCIP capability failure. Homebrew SCIP solves it in 417s; the four
  August runs used a 5-minute timeout that cut it off at 300s. At a 10-minute timeout Homebrew HiGHS and
  SCIP both solve **94/94**. It is a performance gap, not a capability gap -- and a reminder that the
  timeout setting decides which of those a result looks like.
- The guess that **PaPILO and snauty explain OR-Tools' advantage was backwards.** The faster build has
  neither; the slower build has both. On these five models their presence correlates with being slower.
  One comparison, five models, and the builds differ in several other ways -- so this inverts the prior
  rather than settling it. But it is the Dockerfile's own configuration that is now under suspicion.

Note also that the 94-model "easy set" was originally derived as the models SCIP (via OR-Tools), HiGHS and
CPLEX could all solve -- so the test set itself is defined relative to a build that is no longer the one
being measured.

### Plan

1. **Reproduce the Docker build locally, for arm64.** A Linux `.so` out of the image will not load on
   macOS, so this means running the Dockerfile's cmake invocations on the Mac -- or running the benchmark
   inside a container built from the service image, which is more faithful but a bigger lift.
2. **Selecting the library is done.** `Configuration.libraries` maps a contender to an absolute path, and
   `ForkedTask` loads it before the integration initialises. One build per solver per run -- worker JVMs
   are reused and a loaded library stays loaded, so builds are compared across runs, not within one.
   `MIPLIBSolverBuilds` is set up for exactly this.
3. **Run `MIPLIBSolverBuilds` per configuration.** Docker first, since it is the only untested one and the
   only one that ships. Then the full 94 rather than the 5-model set, once there is a reason to.
4. **Bisect the SCIP switches, treating PaPILO and snauty as suspected costs.** The measurement above
   points the opposite way to the initial guess, so the order is: build Docker-as-specified, then
   `PAPILO=off`, then additionally `SYM=off`. If either recovers OR-Tools-like times on
   `neos-2624317-amur`, that is the answer and it is a one-line Dockerfile change. If neither does, the
   difference lies elsewhere -- SoPlex version, compiler flags, or a different 10.0.x patch level (neither
   binary exposes a version string, so that last one is currently unverifiable).
   For HiGHS, bisect the version range and read the upstream changelog for the affected releases.
5. **Feed the answer back into the Dockerfile.** If PaPILO or snauty is responsible, that is a build-flag
   change. If it is a HiGHS version regression, that is a pin change. Either way `HIGHS_VERSION` /
   `SCIP_VERSION` and the switch block become benchmark-informed rather than inherited.

Item 5 (native version logging) is a prerequisite: without it, results from this matrix cannot be
attributed to a build with confidence.

### PaPILO sweep, all 94 models, 2026-08-13 -- RESOLVED

Run through SCIP's own CLI (so unaffected by item 3b), Homebrew build, 120s cap, with and without
`presolving/milp/maxrounds 0`:

| | PaPILO on | PaPILO off |
|---|---|---|
| Solved | 94/94 | 94/94 |
| Total | 595.3s | 536.5s |
| Geo-mean off/on | -- | 0.90 |
| Faster without / slower without / within 10% | | 14 / 11 / 69 |

Not a capability question, and the aggregate is carried by two models. Filtering to differences over one
second in absolute terms it is dead even -- noswot -57.3s, neos-2624317-amur -19.2s, timtab1 -2.4s against
timtab1CUTS +15.3s, aflow30a +3.8s, ic97_tension +1.8s.

The interesting part is the node counts. PaPILO is not merely spending presolve time, it sometimes leaves
a formulation that branches far worse: noswot 920,362 nodes against 40,715, pipex 106 against 1,
sp150x300d 752 against 49, neos-2624317-amur 12,088 against 1,699.

**No predictive rule exists in the available features.** Median variables 348 (hurts) vs 304 (helps);
density 0.496 vs 0.322 with the *neutral* group highest at 0.907; solve time gives 0.90 / 1.01 / 0.82
across the <1s / 1-10s / >10s buckets, a U-shape rather than a trend. Fitting a rule to a 14-11 split on
these numbers would be fitting noise.

**Decision: keep `PAPILO=on` in the Dockerfile, disable it at solve time below a size threshold.**
Compiling it in costs nothing and preserves the choice; compiling it out removes it permanently. The
threshold is implemented in `SolverSCIP.Configurator` as `PAPILO_THRESHOLD = 1_000` constraints -- a first
guess, mirroring the HiGHS parallelism gate, to be tuned once there is data on larger models.

Note the whole sweep is on models under 1k variables, which is exactly where PaPILO has least to offer.
It says nothing about the sizes the service actually solves, and that is the missing measurement.

### The CLI and the ojAlgo path disagree -- do not tune one using the other

The sweep above ran through SCIP's own `scip` binary. The threshold it motivated acts on the **ojAlgo
integration path**. Those are not interchangeable, and measuring both gives opposite answers:

| measured through | PaPILO off vs on | verdict |
|---|---|---|
| `scip` CLI, 94 models, original MPS files | geo-mean **0.90** | disabling PaPILO is ~10% *faster* |
| ojAlgo integration, 94 models, gate on vs off | geo-mean **1.13** | disabling PaPILO is ~13% *slower* |

Same solver build, same 94 models, opposite conclusions. The ojAlgo run also caught the thing the sweep
could not: `neos-2624317-amur` goes from a 5-minute TIMEOUT to **23.0s**, taking SCIP from 93/94 to
**94/94**. So the gate is clearly right for the one model it was aimed at and mildly wrong for the rest.

Caveats on the 1.13 figure: the baseline is the 1-worker August run against a 2-worker gate run, and both
are single-solve cold timings, so some of it is configuration rather than PaPILO. The per-model swings
behind it (supportcase14 4.5x slower, bell3a 3.0x, against sample2 and bppc8-02 improving) are small
absolute numbers on sub-second models, well inside the permutation variability characterised in 3c.

**What this means practically:**

1. The threshold is not yet validated. The measurement that would settle it is PaPILO on/off **through the
   ojAlgo API**, same worker count, same timeout, only the threshold moved. Neither run so far does that.
2. More generally, any future tuning of solver parameters has to be measured through the path that will
   actually use them. The CLI is convenient and fast, and it will mislead.
3. This also puts a bound on 3b: whatever the ojAlgo path does differently, it is enough to invert a 10%
   effect. That is consistent with the residual ~3.6x and worth remembering as evidence that 3b is real.

### Historical note

OR-Tools was on the classpath as the route to SCIP before `ojalgo-scip` existed. It bundles its own
`libhighs.1.dylib` (1.12.0) and both `libscip.10.0.dylib` and `libscip.9.2.dylib`, and whichever library
loaded first won the symbol lookup. That silently invalidated every HiGHS result prior to the lazy-loading
fix in `AbstractBenchmark.INTEGRATIONS`, and changed SCIP's binding too. Because `ForkedTask` now resolves
exactly one integration per forked JVM, OR-Tools is safe to keep as a contender -- and the same mechanism
is what makes the build matrix above tractable.

## 3b. The model ojAlgo hands SCIP is worse than the same file read by SCIP itself

Found while investigating item 3. **Mostly resolved by the PaPILO threshold** (see item 3), but a residual
gap remains.

`neos-2624317-amur`, Homebrew SCIP 10.0.3, same `.mps` file:

| Path | PaPILO | Time |
|---|---|---|
| `scip` CLI | on | 24.8s |
| ojAlgo integration | on | **417s** -- 17x the CLI |
| `scip` CLI | off | 5.9s |
| ojAlgo integration | off (now the default below 1k vars) | **21.4s** -- 3.6x the CLI |

So PaPILO was most of it: ojAlgo's formulation provokes behaviour from PaPILO that SCIP's own reader does
not, and the 17x amplification is gone now that PaPILO is off below the threshold. With the threshold in
place ojAlgo/Homebrew does this model in 21.4s against OR-Tools' 28.6s -- the Homebrew build was never the
problem.

**What is left is a consistent ~3.6x between the ojAlgo path and the CLI**, present with PaPILO out of the
picture entirely. Smaller, but it applies to every model and every solver, so it is still worth chasing.

### The `LinkedHashMap` fix - deferred, not rejected

`ExpressionsBasedModel.myExpressions` is a `HashMap`, so `constraints()` hands solvers the constraints in
`String.hashCode()` order of their names. Consequences: renaming a constraint silently reorders the model,
a model written back out matches nothing the user wrote, and no other tool reading the same file sees the
order ojAlgo produces. `LinkedHashMap` fixes it in one word and was verified to reproduce input order
exactly.

Measured cost, `snapshot()` per branch-and-bound node:

| expressions | HashMap | LinkedHashMap | |
|---|---|---|---|
| 15 | 0.53 us | 0.53 us | 0% |
| 342 | 9.35 us | 9.76 us | +4% |
| 747 | 21.10 us | 22.05 us | +5% |
| 5208 | 101.09 us | 114.48 us | +13% |

Not free, and worst on the largest models. Note this cannot be measured end to end - reordering changes
the search tree, and the permutation experiments show that swamps any copy-cost difference.

**Deferred deliberately**: the change is right, but it makes `CuteNetlibCase#testMODSZK1` fail by landing
on the ordering that triggers 3c, and it will move ojAlgo-MIP benchmark numbers in both directions on many
models. Worth doing when there is room to handle those consequences - not as a drive-by.

The original evidence below still stands as the starting point.

Nothing in `SolverSCIP.Configurator` explains it: with the benchmark's settings it sets only
`display/verblevel`. No time limit, no node limit, and `parallel/maxnthreads` needs `parallelism > 18`
which is unreachable. So the runtime configuration is effectively stock in both cases.

Nor is it a gross structural difference. SCIP's own reader reports 524 variables (180 binary, 344 integer,
0 continuous) and 342 constraints; ojAlgo reports 524 variables and 343 expressions, the extra one being
the objective. Same problem, same size.

That leaves how the problem is *expressed* - bounds, ranged constraints, variable type marking, row
ordering, coefficient scaling - somewhere between `ExpressionsBasedModel.parse`, `simplify()` and the C
API calls in `SolverSCIP.Integration.build`.

The interaction appears to be with PaPILO specifically:

| Path | PaPILO present? | Time |
|---|---|---|
| CLI, Homebrew | yes | 24.8s |
| CLI, Homebrew, `presolving/milp/maxrounds 0` | disabled | 5.7s |
| ojAlgo, OR-Tools library | not compiled in | 32.2s |
| ojAlgo, Homebrew library | yes | 417s |

PaPILO costs about 4x from the CLI and about 13x through ojAlgo, on the same library. So the formulation
ojAlgo produces seems to be one PaPILO handles badly. OR-Tools' build escaped only because it has no
PaPILO at all - which means the "OR-Tools build is 13x faster" result in item 3 is mostly this issue, not
a property of the build.

### To do

1. **Dump both formulations and diff them.** `SCIPwriteOrigProblem` after ojAlgo has built the model,
   against `scip -c "read x.mps" -c "write problem"`. That should show directly what differs.
2. **Check `simplify()` in isolation** - parse, write out, and compare against the input, without any
   solver involved.
3. Once the difference is known, decide whether it is an ojAlgo bug or a formulation choice that happens
   to be pathological for one presolver.

Worth doing before item 3's build matrix: a 17x penalty that follows the integration rather than the build
will distort every build comparison run through it.

## 3c. LinearSolver returns OPTIMAL for an infeasible solution on MODSZK1 (dual + dense) -- RESOLVED

**Root cause found and fixed 2026-08-16 -- see item 9.** MODSZK1 was one symptom of degraded dual
steepest-edge pricing; it now solves to 320.619729 under both map orderings. The analysis below is kept
because the diagnosis method (ordering sensitivity as a signal, `validate()` as the discriminator) is what
eventually led to the real cause, and because the "would a validity assertion catch this?" question is
answered emphatically yes -- see item 9's note on measurement blind spots.

Surfaced by the `LinkedHashMap` experiment (3b), but **not caused by it** - file order simply happens to
be one of the orderings that triggers it. The change is deferred; this bug is not.

```
MODSZK1 (netlib), default options:
  LinkedHashMap ordering:  OPTIMAL  value=854.0042485197   validate() == false   <- test FAILS
  HashMap ordering:        OPTIMAL  value=320.6197290628   validate() == false   <- test PASSES
```

Both return a solution that violates the model's own constraints. The `HashMap` case passes only because
its objective lands within tolerance of the expected value. The solver reports OPTIMAL either way, and a
caller has no way to notice short of validating every result.

### How common

Thirty random constraint orderings of MODSZK1, plus the original, all through dual+dense, classified by
how badly the returned solution violates the constraints:

| | count | |
|---|---|---|
| `validate()` passes | 6 | |
| violations < 1e-6 | 21 | numerical residual, not a wrong answer |
| **violations > 1e-6** | **4** | **genuine failure** |

The two categories line up exactly: correct objective goes with ~1e-8 residual, wrong objective goes with
gross infeasibility. Nothing in between - the solver either reaches the right vertex or ends up somewhere
it should not.

| ordering | objective | max violation |
|---|---|---|
| original file order | 854.00 | 832 |
| shuffle 01 | 783.69 | 6.95e+04 |
| shuffle 23 | 5712.84 | 8.84e+04 |
| shuffle 29 | 330.29 | 8.84e+04 |

So roughly **4 in 31 orderings (13%)** fail for real. The 21 sub-1e-6 cases are an accuracy question about
how strict `validate()` is by default, not evidence of a bug.

### Localised: dual + dense

| algorithm | sparse | value | valid |
|---|---|---|---|
| primal | true | 320.619729 | false |
| primal | false | 320.619729 | false |
| dual | true | **320.619729** | **true** |
| **dual** | **false** | **854.004249** | **false** |

- `dual + sparse` is the only configuration that gets it entirely right.
- MODSZK1 is 1620 variables x 688 expressions, and the default picks dual+dense for it.
- Presolving is not involved - `clearPresolvers()` changes nothing.
- The returned point violates **573 of 688 constraints**, worst by 832.1, median 3.5. Equality rows
  (`0 <= ROW <= 0`) off by tens or hundreds. Not numerical noise.

### Would a validity assertion in the test harness catch this?

`ModelFileTest.assertValues` checks the objective value and never calls `model.validate(result)`. Adding
one would catch the four real failures - but it would also flag the 21 solves that are correct to ~1e-8,
so it needs a deliberate tolerance rather than the default. Worth doing to find out whether MODSZK1 is one
model or many, but it is a considered change, not a free one.

Note that in this instance the objective assertion did its job: every genuinely infeasible result also had
a wrong objective. A validity check would add value mainly if some model somewhere returns the right
objective with a badly infeasible solution, which has not been observed.

Separately: **primal returns the correct objective but fails `validate()`** in both sparse and dense. On
the evidence above that is most likely the same ~1e-8 residual rather than a defect - worth confirming
before treating it as a bug.

`CuteNetlibCase#testMODSZK1` covers it and fails in isolation, so it is a clean reproducer - but only
while the constraint order is the one `LinkedHashMap` produces. With the current `HashMap` it passes,
because that ordering happens to land on a correct objective.

Also seen in the same run: `MIPLIBTheEasySet#testSupportcase16` (288 vs 289.00000000000006) fails in a full
suite run but passes in isolation - that one looks like test interaction through shared static state
rather than the same bug, and wants separate investigation.

## 4. ojAlgo capability gaps -- cut generation

42 of 94 easy-set models fail (ojAlgo-MIP TIMEOUT). Every one is solved by HiGHS; all but
`neos-2624317-amur` by SCIP. Full table sorted by HiGHS time:

| Model | HiGHS | SCIP | vars x expr |
|---|---|---|---|
| vpm1 | 8ms | 11ms | 378 x 235 |
| sample2 | 11ms | 31ms | 67 x 46 |
| nexp-50-20-1-1 | 26ms | 132ms | 490 x 541 |
| p0548 | 34ms | 38ms | 548 x 177 |
| set1cl | 37ms | 9ms | 712 x 493 |
| set1al | 37ms | 38ms | 712 x 493 |
| fixnet3 | 46ms | 15ms | 878 x 479 |
| sp150x300d | 53ms | 128ms | 600 x 451 |
| opt1217 | 76ms | 53ms | 769 x 65 |
| sentoy | 91ms | 157ms | 60 x 31 |
| beavma | 182ms | 165ms | 390 x 373 |
| fixnet4 | 184ms | 235ms | 878 x 479 |
| set1ch | 234ms | 120ms | 712 x 493 |
| modglob | 375ms | 81ms | 422 x 292 |
| bell4 | 399ms | 566ms | 117 x 106 |
| blend2 | 506ms | 484ms | 353 x 275 |
| pp08a | 638ms | 504ms | 240 x 137 |
| fixnet6 | 1.3s | 3.6s | 878 x 479 |
| neos-3610051-istra | 1.6s | 628ms | 805 x 710 |
| 22433 | 1.7s | 720ms | 429 x 199 |
| neos-3610173-itata | 1.8s | 346ms | 844 x 748 |
| mik-250-20-75-5 | 1.8s | 2.3s | 270 x 196 |
| mik-250-20-75-3 | 1.8s | 1.5s | 270 x 196 |
| mik-250-20-75-2 | 1.8s | 3.2s | 270 x 196 |
| exp-1-500-5-5 | 2.1s | 1.1s | 990 x 551 |
| mik-250-20-75-1 | 2.2s | 1.5s | 270 x 196 |
| neos17 | 3.1s | 2.3s | 535 x 487 |
| gsvm2rl3 | 4.8s | 3.2s | 241 x 181 |
| neos-2624317-amur | 6.5s | FAIL | 524 x 343 |
| aflow30a | 6.6s | 7.3s | 842 x 480 |
| mik-250-1-100-1 | 6.7s | 41.4s | 251 x 152 |
| mik-250-20-75-4 | 8.7s | 7.5s | 270 x 196 |
| ran12x21 | 9.8s | 24.2s | 504 x 286 |
| ic97_tension | 11.4s | 6.0s | 703 x 320 |
| r50x360 | 16.4s | 17.2s | 720 x 411 |
| rout | 16.6s | 4.1s | 556 x 292 |
| timtab1 | 26.5s | 29.1s | 397 x 172 |
| ran16x16 | 30.8s | 26.8s | 512 x 289 |
| timtab1CUTS | 31.5s | 59.8s | 397 x 372 |
| noswot | 40.3s | 5.0s | 128 x 183 |
| prod2 | 66.3s | 57.0s | 301 x 212 |
| graphdraw-gemcutter | 75.6s | 48.4s | 166 x 475 |

Not a constant-factor problem. Models with 60-240 variables are solved in 8-638ms by the natives and not
at all by ojAlgo in 5 minutes. Something structural is absent.

### The diagnosis: cutting planes

The decisive piece of evidence is a natural experiment already present in the test set: **ojAlgo fails
pp08a but solves pp08aCUTS in 213s.** The MIPLIB `*CUTS` variants are the same underlying model with cut
constraints already added to the formulation. ojAlgo can solve the instance where the cut generation was
done for it, and cannot solve the instance where it must generate them itself. That isolates the gap to
cut generation rather than to branching, heuristics or LP performance.

pp08a has 136 rows and an LP relaxation of 2748. pp08aCUTS has 246 rows (110 added cuts) and an LP
relaxation of 5481. The optimal MIP value is 7350. So the 110 cuts close 59% of the integrality gap.
Without them, branch-and-bound must enumerate a tree that is exponentially larger.

The failures cluster into families, and the families are the textbook cases for specific cut classes:

- **fixnet3 / fixnet4 / fixnet6**, **modglob**, **p0548**, **vpm1** -- fixed-charge network flow.
  Standard attack: **flow cover cuts** (and generalised flow covers).
- **set1al / set1ch / set1cl** -- same 712x493 set-partitioning base, three objectives.
  Standard attack: **clique cuts** and **knapsack cover cuts**.
- **mik-250-\*** (6 models) -- mixed-integer knapsack problems.
  Standard attack: **MIR (mixed-integer rounding) cuts**.
- **ran12x21 / ran16x16** -- random dense MIP.
  Standard attack: **Gomory + MIR** in rounds.
- **timtab1 / timtab1CUTS** -- even with cuts pre-added ojAlgo fails. Branching or search-strategy issue
  on top of the cut gap.

### What ojAlgo has today

`IntegerSolver` has GMI (Gomory Mixed-Integer) cuts, implemented in `TableauCutGenerator` and wired through
`NodeSolver.doGenerateCuts`. The infrastructure:

- **Root cut loop** (`IntegerSolver.generateRootCuts`): up to 10 rounds of GMI at the root node before
  branching begins. Stops on no cuts generated, LP infeasibility, or tailing-off (<1E-6 relative
  improvement).
- **In-tree cuts** (`ModelStrategy.DefaultStrategy.isCutRatherThanBranch`): spaced by >=5 depth levels and
  >=100 nodes since last success. Permanently disabled after 5 consecutive failures.
- **Cut interface**: `UpdatableSolver.generateCutCandidates` returns a collection of `Equation` objects
  derived from simplex tableau rows. `NodeSolver` translates them back to `Expression` objects on the
  `ExpressionsBasedModel`.

What is **entirely missing**:

1. **Flow cover cuts** -- no detection of fixed-charge / variable upper bound structure
2. **Knapsack cover cuts** -- no knapsack separation
3. **Clique cuts** -- no conflict graph, no clique detection
4. **MIR cuts** -- no mixed-integer rounding beyond what GMI implicitly provides
5. **Structural detection** -- no classification of constraints by type (set packing, set partitioning,
   flow conservation, knapsack, VUB/VLB)

GMI cuts alone are too weak for these model families. GMI cuts are derived from the simplex tableau and
depend on the current basis -- they have small coefficients, they are dense (touching many variables), and
they tend to become parallel to each other after a few rounds. The model-specific separators (flow cover,
knapsack cover, clique) exploit combinatorial structure visible in the original formulation and produce
cuts that are sparse, deep, and complementary to GMI.

### Suggested order of work

1. **Start with pp08a vs pp08aCUTS.** Diff the two formulations to see exactly which cuts close the gap,
   then work backwards to which separator would produce them. It is the smallest reproducer with a known
   answer: 110 cuts, 59% gap closure, from a 240x137 model. Analysis is in progress.
2. **Flow cover cuts** -- unlocks the largest cluster (fixnet x3, modglob, p0548, vpm1, aflow30a).
   These models have the fixed-charge pattern: continuous flow variable `f` with `0 <= f <= u*y` where
   `y` is binary. A flow cover cut for a set S with excess `lambda = sum(u_j, j in S) - b` is:
   `sum(f_j, j in S) - sum((u_j - lambda)+ * y_j, j in S) <= b`.
   Requires detecting VUB constraints and building a knapsack over the capacities.
3. **Knapsack cover cuts** -- unlocks the set1 family (3 models) and helps mik-250 (6 models).
   Given `sum(a_j * x_j) <= b` with `x` binary, find a cover `C` with `sum(a_j, j in C) > b` and add
   `sum(x_j, j in C) <= |C| - 1`. Lifting strengthens the cut by adding back non-cover variables with
   calculated coefficients.
4. **Presolve and probing** -- several of these are effectively solved by the natives before search
   begins, so measure how much of the gap closes before any cut is generated.

### Implementation notes

The cut generation pipeline needs two layers that don't exist yet:

**Model-level structure detection** (runs once, before branching):
- Scan `ExpressionsBasedModel.constraints()` for patterns: set packing (all-ones `<=`), set partitioning
  (all-ones `=`), VUB (`f - u*y <= 0`), knapsack (non-negative coefficients, binary variables, `<=`).
- Build a conflict graph from set-packing / clique constraints.
- This information is static and can be cached per model.

**Separator interface** (called at each cut round):
- Unlike GMI, these separators work from the `ExpressionsBasedModel` and the current LP solution, not from
  the simplex tableau. They need the variable values, not the tableau rows.
- The existing `UpdatableSolver.generateCutCandidates` interface returns `Equation` objects from the
  tableau. Model-level separators would return `Expression` objects directly, bypassing the tableau
  entirely. This is a different hook -- probably on `NodeSolver` directly, alongside the GMI call.

## 5. Log the native library version each integration binds to

Both C APIs expose it: `Highs_version`, and `SCIPmajorVersion` / `SCIPminorVersion` / `SCIPtechVersion`.
Print it alongside the `Environment:` line in the benchmark header.

This would have caught the HiGHS 1.12.0-instead-of-1.15.1 problem on the first run rather than after
several. It is also what makes item 3 verifiable instead of inferred, which matters as soon as results are
being compared against Docker image builds.

## 6. ojAlgo instability -- correctness, not performance

Four models fail as UNSTABLE rather than TIMEOUT: **22433**, **blend2**, **sample2**, **sentoy**. The
`UNSTABLE` classification means repeated executions of the same pair disagreed.

22433 has been unstable throughout. blend2, sample2 and sentoy became unstable only at 18 threads, having
solved reliably at 9 -- so raising the thread count traded three reliable solves for a 22% throughput gain.

Establish whether the disagreement is in the objective value or only in the solution vector. The first is
a correctness bug; the second is cosmetic but still worth understanding. Either way this argues for
caution before raising ojAlgo's default parallelism.

## 7. Objective constants -- MPS is correct, the reference value was not

This item previously claimed ojAlgo drops objective constants on both the file and the API path. Re-tested
2026-08-15 against the solvers directly: **that was wrong for MPS**. ojAlgo reads and applies objective
constants correctly and agrees with every other solver. Only the service-client path is affected.

### The standard, measured

MPS carries an objective constant as an RHS entry on the `N` row, holding the **negative** of the constant.
Minimal model `min x` subject to `x >= 2`, with and without `COST -7.0`:

| solver | plain | with `COST -7.0` |
|---|---|---|
| HiGHS CLI | 2 | 9 |
| SCIP CLI | 2 | 9 |
| ojAlgo | 2 | 9 |

Same conclusion on E226, ablating the `-7.113` entry from its RHS line:

| solver | as shipped | entry removed |
|---|---|---|
| HiGHS | -11.638929066 | -18.751929066 |
| SCIP | -11.6389290663705 | -18.7519290663705 |
| ojAlgo | -11.638929066 | -18.751929066 |

CPLEX documents the same convention -- an objective offset is stored negated on the `N` row. Not measured
here, no licence installed.

### ojAlgo: nothing to fix

The chain is complete and round-trips:

- `FileFormatMPS:341` -- `addObjectiveConstant(value.negate())` on read
- `ExpressionsBasedModel:1682` -- `objective()` calls `retVal.setConstant(this.getObjectiveConstant())`
- `FileFormatMPS:593` -- negated again on write

`myObjectiveConstant` is live. The old note about `myObjectiveAdjustment` never being set is accurate but
beside the point: the constant reaches the result through `setConstant`, not through the adjustment.

`addObjectiveConstant` and `getObjectiveConstant` stay **package-private by design**. Objective constants
are not a publicly supported modelling feature -- they are supported only as far as reading standard file
formats requires. No public setter is wanted.

### What the problem actually was

`NETLIB.solu` listed E226 at -18.751929066, taken from the Netlib README table, which is the optimum
**without** the offset. Every contender returns -11.638929066, the value **with** it, so the benchmark
scored all five WRONG on a model they all solved correctly:

```
E226  HiGHS             WRONG  -11.63892906637049   != -18.751929066
E226  ojAlgo-LP-dual-D  WRONG  -11.638929066371311  != -18.751929066
      ... and the same for the other three configurations
```

Corrected. E226 is the only model in the shipped set with a nonzero objective RHS entry -- all 97 scanned.

### optimisation-service-client, `OptObjective` -- the one real gap

`OptModel.toBytesOfEBM()` disposes of constants by folding them into the bounds:

```java
if (constant != null) {
    if (lower != null) { lower = lower.subtract(constant); }
    if (upper != null) { upper = upper.subtract(constant); }
}
```

Exact for a constraint. For the objective both bounds are null, both branches are skipped and the constant
is never serialised, so the server solves without it and returns a value short by exactly that constant.
Note this is a constraint-side mechanism -- bounds have nothing to do with an objective constant, which is
why the objective falls through it.

**Fix, given ojAlgo keeps its API closed**: keep the constant client-side. Do not serialise it; add it to
the value read off the service `Result`. `OptObjective.getValue()` already does this -- it recomputes from
the variable values and adds the constant back -- so a caller using that accessor is already correct and
only one reading `Result` directly is not. Nothing needs to cross into ojAlgo. Worth checking which of the
two the API steers people toward.

## 8. Tuning the automatic LP solver selection -- selection is not the lever

> **Superseded in part by item 9 (2026-08-16).** Everything below was measured against a solver whose dual
> pricing was broken, so the per-configuration failure counts are no longer current -- several models
> attributed to "no configuration handles this" were the pricing defect, not the configuration. The two
> structural conclusions still hold: there is no primal/dual heuristic in `build()`, and the sparse/dense
> choice is the only real one. Re-measure before acting on the numbers.

From the Netlib run of 2026-08-15 (`results/2026/08/netlib_4oj_output.csv`), four forced ojAlgo
configurations plus HiGHS as reference, validated against `NETLIB.solu`.

| configuration | solved | fastest on | genuine WRONG |
|---|---|---|---|
| HiGHS | 97/97 | -- | 0 |
| ojAlgo-LP-dual-S | 90/97 | 6 | 4 |
| ojAlgo-LP-dual-D | 88/97 | 65 | 8 |
| ojAlgo-LP-prim-D | 79/97 | 15 | 4 |
| ojAlgo-LP-prim-S | 73/97 | 0 | 3 |

Dense is 2.56x faster than sparse (geometric mean) and the lead shrinks with size: 3.45x below 300
variables, 1.89x above 1500. ojAlgo-dual-D is 1.71x slower than HiGHS.

### What the automatic path actually decides

**Primal vs dual: nothing is decided.** `LinearSolver.ModelIntegration.build()` uses the dual solver
unless the caller asked for primal:

```java
boolean newerDualSolver = true;
if (Boolean.FALSE.equals(model.options.linear().getDualOrPrimal())) { newerDualSolver = false; }
```

The class javadoc says "depending on the `Configuration` and problem characteristics" -- only
`Configuration` is read. Either implement the second half or correct the javadoc.

**Sparse vs dense** is the only real heuristic, in `SimplexStore.newStoreFactory`:

```java
if ((size > 2_400_000L && ratio > 3.5) || size >= 25_000_000L || ratio >= 11.0) {
    return new RevisedStore(structure);   // sparse
} else {
    return new DenseTableau(structure);   // dense
}
```

Netlib models sit below those thresholds, so **automatic == dual + dense** across this set: the fastest
configuration and the least correct one. For speed alone the thresholds are directionally right -- the
dense advantage does decay with size -- so this section is not an argument for moving them.

### Re-tuning thresholds cannot fix the failures

Ten models where at least one forced configuration failed, run through plain `minimise()`:

| model | dual-D | dual-S | prim-D | prim-S |
|---|---|---|---|---|
| GREENBEB | wrong 2.4e20 | **OK** | unstable | timeout |
| PILOT-JA | wrong -4074 | **OK** | wrong -6574 | timeout |
| MODSZK1 | wrong 854.0 | **OK** | **OK** | **OK** |
| TRUSS | wrong 992255 | wrong 4.9e7 | **OK** | timeout |
| D2Q06C | unbounded | wrong -781 | **OK** | timeout |
| PILOT | -557.2165 (closest) | wrong -413.5 | infeasible | timeout |
| GREENBEA, PILOT87, QAP12, QAP8 | \- | \- | \- | \- |

Dual-sparse rescues two, primal-dense rescues two, and the two groups do not separate by size, density or
any other property visible before the solve. The last four are handled by no configuration at all. So an
a-priori rule -- however tuned -- cannot recover these.

### The mechanism that fits the data: validate and retry

After the default dual+dense solve, check primal feasibility and the duality gap; on failure re-solve on a
different store. Free on the 88 models that already pass, and it is the only approach the table above
supports. Ties into 3c, where the returned point violates 573 of 688 constraints while the state says
OPTIMAL -- the detection half already has a known reproducer.

### Cross-check: the suite already knew

Nine of these ten carry `@Tag("unstable")` in `CuteNetlibCase` (22 of 114 tests do). The benchmark did not
find new bad models, it counted the known ones.

**MODSZK1 is the exception and is a real regression.** Not tagged unstable; correct under 57.0.0 and
57.1.0, wrong (854.004) under the current 57.1.1-SNAPSHOT with the `LinkedHashMap` change in place. See
3c -- this is the one item here that is a plain bug rather than a known-hard model.

### The published 99% and this 91% are not in conflict

<https://www.ojalgo.org/2026/06/lp-qp-performance-v57/> reports "96 of 97", which is the solve rate --
no crash, no timeout. `NETLIB.solu` and the HiGHS reference solver were both added after that run, so no
objective value was ever checked. Verified, the same configuration is 88/97 (91%). Not a version effect:
57.0.0 and 57.1.0 fail GREENBEA, PILOT87, QAP8 and TRUSS identically. Worth a correction to the article
once the retry work lands.

## 9. Dual steepest-edge weights degrade without bound -- FIXED 2026-08-16

The largest LP correctness defect found so far, and the cause behind 3c and much of 8. Fixed in ojAlgo on
branch `fix_lp`.

### Symptom

`state=OPTIMAL`, `validate()==false`, every variable bound respected, 70-83% of constraint rows violated,
objective always worse than the true optimum. Ten netlib models affected.

### Root cause

Dual pricing scores candidates as `(infeasibility * infeasibility) / edgeWeight`. The weight update was

```java
edgeWeights[i] += ratio * ratio * w_p;   // every non-pivot entry, every iteration
edgeWeights[p] = ONE;                    // only the pivot row ever decreases
```

Monotonically increasing, with no reference-framework restart. On QAP8 the weights reach **~1E304**, at
which point every score underflows below `MACHINE_SMALLEST`:

```
-0.023809523809334064 / 2.090764070432085E304 = 2.711417476722227E-308
```

`getDualExitCandidate` then finds no candidate and reports primal feasibility attained -- while the
solver's own `!PF` debug output lists 207 basic variables outside their bounds. One primal optimality check
follows and returns OPTIMAL.

Devex uses `max`, not `+=`, and requires a restart when weights grow. Neither was present.

### The fix (three parts, all needed)

1. `max` instead of `+=` for non-pivot entries.
2. **Devex pivot-row update** `edgeWeights[p] = max(w_p / (pivot * pivot), 1)` instead of a hard `1`.
   Without this, weights on small problems can never leave 1.0 -- which is what `EdgeWeightTest` was
   reporting. This part also removed a large speed regression.
3. Reference-framework restart at `DEVEX_LIMIT = 1E8`.

Plus a matching threshold change -- `INFEASIBILITY` was doing two different jobs and had to be split:

| constant | role |
|---|---|
| `INFEASIBILITY` = `of(10)` | does a residual count as infeasible (`getDualExitCandidate`, dual no-entry branch) |
| `RATIO_RELAX` = `of(9)` | how far a basic variable may drift outside bounds during a primal pivot |

The accurate weights reach vertices with smaller residuals, so the old `of(9)` rounded a genuine 1.2E-8
infeasibility away to OPTIMAL. `of(11)` is too tight -- MAROS then returns a solution failing validation.
**`of(10)` is the only value that passes the whole suite.**

### Measured

| configuration | netlib objective | Meszaros infeasible | CuteNetlibCase |
|---|---|---|---|
| baseline | 89/97 | 21/21 | 86/86 |
| Devex (`max` + restart) | 93/97 | 21/21 | 86/86 |
| + pivot-row update, `of(9)` | 95/97 | **20/21** | 86/86 |
| + pivot-row update, `of(11)` | 95/97 | 21/21 | **fails MAROS** |
| **+ pivot-row update, `of(10)`** | **94/97** | **21/21** | **86/86** |

Time over the 89 commonly-solved models: 43.3s -> 32.9s (**24% faster**). Not uniform -- MAROS-R7 -42%,
80BAU3B -54%, STOCFOR2 -58%; PILOT-JA +124%, CYCLE +82%.

### Primal stall in `SimplexTableauSolver` -- attempted and REVERTED

**Policy: leave `SimplexTableauSolver` alone.** It is the old primal tableau family, reached only when a
caller sets `options.linear().primal()`, and it has decades of behaviour depending on it. Every attempt
below made things worse. Improvements belong in the newer dual `SimplexSolver`, which is the default path
and where the measured gains actually came from.

The defect is real: QAP8 primal+dense reaches the exact optimum by ~10k iterations, then pivots forever
with step lengths of ~1E-13 -- Dantzig pricing with no anti-cycling. It now reports FEASIBLE at the time
limit with the correct value (203.500000, 8E-13) and optimality unproven. That is an honest failure and is
where it should stay until there is a better idea.

**What was tried, and what it cost.** Detecting the stall is easy -- count consecutive degenerate phase-2
pivots, using the `DEGENERATE` context, not `MACHINE_EPSILON`, which is tighter than the actual steps and
never fires. Deciding what to *report* is where it falls apart:

1. **Declare OPTIMAL past a limit.** Unsound. No number of degenerate pivots implies optimality: a
   degenerate vertex has many bases, it is optimal iff *some* basis at it prices out, and the current one
   may simply not see the improving edge.
2. **Gate on the reduced cost.** Measured wrong. Reduced costs belong to the basis, not the vertex -- at
   QAP8's stall the entering column priced at **-1.574**, nowhere near noise, while the point was already
   the exact optimum. Demotes a correct OPTIMAL to FEASIBLE and certifies nothing.
3. **Certify via `findEscapeColumn()`** -- a vertex is optimal iff no improving column admits a non-zero
   step, so scan for one; take it if found (which also breaks the stall), declare OPTIMAL if none. Sound in
   principle, **wrong in practice**: it broke four models that had solved correctly.

| model | with the certificate | reverted | expected |
|---|---|---|---|
| KB2 | **0.0** | -1749.90013 | -1749.9 |
| SCSD1 | 9.00000002 | 8.66666667 | 8.6667 |
| STOCFOR2 | -25468.4159 | -39024.4085 | -39024.4 |
| DEGEN3 | -980.600000 | -987.294000 | -987.294 |

All four reported `validate() == true` -- feasible but **suboptimal**, declared OPTIMAL. The trade for
prim-D was gaining QAP8 and TRUSS against losing those four; reverting is worth +2 for prim-D and +2 for
prim-S, measured.

**Why all three failed, and it is the same reason every time:** the tableau cannot distinguish a genuinely
degenerate pivot from a very small real one. The certificate tests "does any improving column admit a
non-zero step" with `DEGENERATE.isZero(minRatio)`, a ~5E-9 threshold, so a column whose true minimum ratio
is 1E-10 is classified as blocked and optimality is certified at a suboptimal vertex. Bland's rule failed
for the sibling reason (ignores pivot magnitude), the stability floor for a third (declining pivots makes
the solver churn). Each needs a reliable zero-test that the accumulated tableau error does not support --
`SimplexTableauSolver` never refactorises.

That is the same conclusion the reference-set experiment reached from the dual side: **the prerequisite is
a refactorisable basis, not a cleverer rule.**

### Automatic selection -- measured, and section 8 superseded

First run of the automatic path itself (`Contender.OJALGO_LP`, `NetlibBench10k`, 8 workers,
`results/2026/08/netlib_10k_8_*`). Previous LP runs only ever exercised the four *forced* configurations.

| solver | solved |
|---|---|
| HiGHS | 97/97 |
| SCIP | 97/97 |
| **ojAlgo-LP (automatic)** | **94/97** |
| Hipparchus | 52/97 |

**94 is the ceiling**, not a shortfall. QAP12 and PILOT-JA are solved by no ojAlgo configuration, and GREENBEA
is unreachable because GREENBEB has byte-identical dimensions (m=2393, n=7798, size=18660614, ratio=3.26)
and needs the opposite store -- no predicate over size and ratio can separate them. Its three failures are
exactly those. Going further needs the GREENBEA timeout fixed in the solver, not more tuning.

The selection constants were retuned to reach it:

```java
if (size > 1_000_000L && ratio > 3.6 || size >= 25_000_000L || ratio >= 11.0) {
```

- `ratio > 3.6` is the working gate. The models that must stay dense sit in a narrow band -- GREENBEB 3.26,
  D2Q06C 3.38, PILOT87 3.40, PILOT 3.53 -- and the large sparse wins sit above it: MAROS-R7 4.00,
  80BAU3B 5.33, TRUSS 9.80. The old 3.5 caught PILOT and lost it, costing a model.
- `size >= 25M` is a memory backstop, not a speed rule: `DenseTableau` allocates `double[m+1][n+1]`, so 25M
  caps it near 200MB. The floor before losing a model is D2Q06C at 15.9M; 20M is the lowest value with
  sensible margin, and costs ~2%.
- Confirmed in the run: PILOT solves, and TRUSS / MAROS-R7 / 80BAU3B are all routed to sparse.

**This supersedes section 8's conclusion that selection is not the lever.** It was measured against the
broken dual pricing. What is true now is narrower: selection cannot exceed 94, the sparse branch can only
lose models on capability, and its whole justification is speed on a handful of large models.

### How ojAlgo compares -- same run, 94 models all three solve

| | geo-mean ratio |
|---|---|
| ojAlgo / HiGHS | 2.36x slower |
| **ojAlgo / SCIP** | **0.96x -- level** |
| SCIP / HiGHS | 2.45x slower |

ojAlgo is faster than HiGHS on 36 of 94 and faster than SCIP on 51 of 94.

The central tendency and the total disagree sharply, and both are worth knowing:

```
total over the 94:   ojAlgo 156.3s    HiGHS 7.4s    SCIP 15.4s
```

ojAlgo matches SCIP on the *typical* model but its losses are concentrated in a few: D2Q06C 36.0s against
HiGHS's 0.4s, then DEGEN3 35x, BNL2 80x, STOCFOR2 103x, KEN-07 and PDS-02 52x. On small models it usually
wins -- AFIRO, RECIPELP, KB2, STOCFOR1, ADLITTLE all 0.03-0.11x -- since there is no native call overhead,
though several of those are sub-millisecond and partly noise.

So the defensible claim is "level with SCIP on the median model, behind on the hard tail". Note the run used
`Parallelism.EIGHT`; contention inflates every absolute time, so compare within this run only.

### Dead ends -- do not retry without new evidence

- **Reverting `dec86d3f`** (the 2026-06-11 relative-infeasibility commit). Looked damning, changes nothing.
  Verified twice, the second time against bound violations in raw solver output rather than final values.
- **Bland's rule** for the primal stall. Terminates, but returns 260.5 against a true 203.5: it ignores
  pivot magnitude, and the tableau is too ill-conditioned by then to survive tiny pivots.
- **Pivot stability floor** in `testPrimalExitRatio` (decline pivots below `factor * maxScale`, re-price).
  Measured at 1E-3/1E-5/1E-7/1E-9: strictly worse at every threshold, 88/97 at 1E-5 -- worse than baseline.
  Declining doesn't remove the need for the pivot; the solver churns and ends further from optimal.
- **Final primal-feasibility check before OPTIMAL** (`isPrimalFeasible()` already exists, is never called).
  Fixes cplex2 but drops netlib 95 -> 85: its tolerance is tighter than the residuals correct solutions
  carry, and cplex2's 1.2E-8 violation falls inside that same band.
- **Porting HiGHS's Devex wholesale** -- see the section below. Two variants, both worse.

### Compared against HiGHS -- and why porting their Devex does not help

Source read at `~/Developer/optimatika_git/HiGHS`, `highs/simplex/`. What they do:

| | HiGHS | ojAlgo now |
|---|---|---|
| default pricing | **steepest edge**, Devex is the fallback it switches down to | Devex-ish only, no mode selection |
| update, other rows | `w_i = max(w_i, new_pivotal * a_i^2)` | `max(w_i, (a_i/alpha)^2 * w_p)` -- uses the **unclamped old** weight |
| update, pivot row | `new_pivotal = max(1, w_p / alpha^2)` | same |
| restart trigger | recurred vs **exact** weight differ by >3x, **or** age > `max(25, 100*nbRows)` | magnitude cap `DEVEX_LIMIT = 1E8` |
| reference set | `devex_index_`, the basic variables at framework start | none |
| magnitude cap | **none** | the only trigger |

`HEkkDual.cpp:2169` (update), `HEkk.cpp:2241` (apply), `HEkkDual.cpp:1515` (`newDevexFramework`),
`HEkkDualRow.cpp:614` (`computeDevexWeight`), `HEkkDual.cpp:2269` (`initialiseDevexFramework`).
HiGHS also **replaces** the recurred weight with the exact one every iteration, not just compares
(`HEkkDual.cpp:2136`).

Implemented faithfully in ojAlgo (`devex-reference-set.patch`): reference set re-taken on restart, exact
pivotal-row weight as the row norm over that set, recurred replaced by exact, restart on 3x divergence or
age. Sequencing verified -- `updateDualEdgeWeights` runs before the pivot, as in HiGHS.

| | test suite | netlib objective | "OK" but `validate()==false` |
|---|---|---|---|
| **current** | **all pass** | **94/97** | **6** |
| reference set, immediate restart | 2 failures (PILOTNOV, GREENBEB) | 95/97 | 12 |
| reference set, deferred restart (as HiGHS) | all pass | 91/97 | 8 |

Deferring the restart until after the pivot fixes the two test failures and costs D2Q06C, PILOT-JA and
PILOT87 instead. Timing a wash throughout (43.1s -> 43.0s).

**Why it fails here.** The "exact" weight is not exact. HiGHS computes it from a factored basis with
periodic reinversion; `SimplexTableau` is a classic tableau with no refactorisation, so the accuracy check
compares a recurred weight against a computed one taken from a tableau that has itself drifted -- the same
error accumulation behind the primal stall above. Restarting on that comparison discards good information
about as often as bad, which is exactly the coin-toss pattern in the table.

**Consequence for any future attempt:** the prerequisite for HiGHS-style pricing is not the reference set,
it is a **refactorisable basis**. Without one, exactness is unavailable and every accuracy-driven mechanism
degrades to guesswork. Do not retry the reference set before that exists.

Also measured and **not** applied: HiGHS's age-based restart on its own (`highs-age-restart.patch`).
Safe -- 91/91, 71/71, 4/4, 32/32, netlib 94/97 with zero verdict changes, 90.0s -> 89.9s -- but neutral on
these sets. Available as insurance for models outside them.

### Measurement blind spot -- important

The benchmark's verdict is objective-value agreement only. That **cannot see an invalid solution**:

- PILOT-JA scores OK while returning `E1EIM05` 927.878 outside its bound, violating 4 rows -- the variable
  has objective coefficient 0, so clamping it leaves the objective matching to 6E-9.
- MAROS scores OK at `of(11)` while `CuteNetlibCase` fails it on `validate()`.

`CuteNetlibCase` asserts value *and* validity and is the better metric. **Add `model.validate(result, ...)`
to the benchmark's verdict** before doing more solver tuning -- otherwise thresholds get selected for
objective agreement while feasibility drifts unmeasured.

### cplex2 is genuinely infeasible -- verified

Third-party, three ways. The models artifact ships `optimisation/meszaros/readme_infeasible.txt`, which is
John W. Chinneck's "SUMMARY OF INFEASIBLE LPs" (3 November 1993, Carleton University) for Netlib's
`lp/infeas` collection:

> CPLEX1, CPLEX2: medium and large problems respectively. ... CPLEX2 is an almost-feasible problem.
> Contributor: Ed Klotz, CPLEX Optimization Inc.

Corroborated by <https://www.netlib.org/lp/infeas/readme> and SuiteSparse `lpi_cplex2`
(<https://sparse.tamu.edu/LPnetlib/lpi_cplex2>), and by HiGHS and SCIP independently. It is *deliberately*
almost-feasible -- infeasible by ~1E-8 -- so it is a must-pass, not a tolerance quibble.

### Test changes

- `EdgeWeightTest.verifyPrimalEdgeWeights` -- dropped the `assertTrue(weightsChanged)` assertion. Devex
  weights are 1.0 until a pivot makes them larger, so on small well-scaled problems they legitimately all
  stay 1.0. The remaining assertions (positive, `< 1E6`, dense and revised stores agree) are the ones
  carrying information. The dual equivalent now passes and was left alone.
- `CuteNetlibCase` tags, re-verified against the fixed solver and confirmed 3/3 on repeat runs:

| test | was | now | time |
|---|---|---|---|
| QAP8, PILOTNOV, TRUSS | unstable | *(enabled)* | 1.0-3.1s |
| MAROS_R7 | slow | *(enabled)* | 4.5s |
| GREENBEB | unstable | *(enabled)* | 7.6s |
| PILOT, PILOT87 | slow, unstable | slow | 13.3s, 30.9s |
| D2Q06C | unstable | slow | 16.3s |

`unstable` count 23 -> 16. Default `CuteNetlibCase` run: 86 -> 91 tests, 0 failures, 31s -> 51.6s.
Note `PILOT_WE` was an un-tag candidate before the fix and now fails -- re-verify, do not trust the
earlier list.

### Still open

**Where to work.** `SimplexSolver` -- the newer dual family -- is the default path and where every measured
gain in this item came from. `SimplexTableauSolver` is old, is only reached via
`LinearSolver.Configuration#primal()`, and resisted three separate attempts; treat it as fixed behaviour and
leave it alone unless there is a specific reason not to.

Verified on the full benchmark 2026-08-16 (`results/2026/08/`), 97 models, expected values from
`NETLIB.solu`. Measured against the pre-LP-work baseline (commit `bc15f4d`), and the dual figures were
identical across two independent runs -- reproducible, not a lucky draw.

| solver | before | after | |
|---|---|---|---|
| **ojAlgo-LP-dual-D** | 87/97 | **95/97 (98%)** | **+8, nothing lost** |
| ojAlgo-LP-dual-S | 88/97 | 91/97 (94%) | +5 / -2 |
| ojAlgo-LP-prim-D | 78/97 | 78/97 (80%) | +5 / -5 |
| ojAlgo-LP-prim-S | 72/97 | 70/97 (72%) | +2 / -4 |

HiGHS was 97/97 in the runs that included it; it is dropped from later runs to save time, which is safe
because `AbstractNetlib.loadExpectedValues` gives every model an expected value and the reference solver is
then never consulted. Keeping it is still worth it when a verdict change is being investigated -- it is the
only clean control, and it is what exposed a bad `VALIDATION` tolerance by being flagged INVALID itself.

- **dual-D** -- fails GREENBEA and QAP12 (timeouts, size) and PILOT-JA (INVALID, below).
- **dual-S** -- lost GREENBEB and PILOT-JA while gaining five. PILOT-JA is a real INVALID detection, not a
  regression; GREENBEB is worth a look, though dual-D solves it.
- **prim-D / prim-S** -- flat and slightly down respectively. The losses are FINNIS, GROW22, PEROLD, WOOD1P
  (and TRUSS for prim-D), all unaffected by any change in this item: they are wrong with and without the
  reverted stall change, and `SimplexTableauSolver` has no other functional change. Not being pursued.

- **QAP12** -- still in phase 1 at the limit. Size, not correctness.
- **PILOT** -- marginal; passes now that the benchmark bar is a single `ACCURACY` of(4).
- **GREENBEA** -- correct at `INFEASIBILITY` of(9) and of(11), times out at of(10). Non-monotonic in the
  threshold, which is worth understanding.
- **PILOT-JA bound violation** -- the one INVALID dual-D still reports. Introduced by a primal pivot inside
  `SimplexSolver`: entering reduced cost 1.4E-7 against `COST` ~1E-7, pivot element 1.4E-6 against `PIVOT`
  ~1E-6 -- both guards barely cleared, their product catastrophic. The stability-floor fix failed (above).
- **Primal stall (QAP8)** -- unfixed by choice. Reports FEASIBLE at the limit with the correct value.
- **`LinkedHashMap`** (3b) -- deferred, but the MODSZK1 blocker is gone: it solves correctly under
  LinkedHashMap ordering now. Re-test if revisited, and note it may explain the prim-D/prim-S decline above.
