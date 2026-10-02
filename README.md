# ojAlgo Mathematical Programming Benchmark (ojMPB)

Benchmarks for [ojAlgo](https://github.com/optimatika/ojAlgo) mathematical programming solvers, comparing against alternative Java and native solver integrations. Uses standard optimisation problem test sets and custom benchmark harnesses.

## Problem test sets

### Linear Programming -- Netlib

Benchmarks using the [Netlib LP test set](https://www.netlib.org/lp/data/). Classes in `org.ojalgo.benchmark.linear.netlib` run the full Netlib suite with configurable problem size filters and solver selection. Test data is provided by the `optimisation-models` artifact.

### Convex Quadratic Programming -- Maros-Meszaros

Benchmarks using the [Maros-Meszaros QP test set](https://www.doc.ic.ac.uk/~im/). Classes in `org.ojalgo.benchmark.convex.marosmeszaros` filter models by type (pure QP, separable) and size, and compare solvers against known optimal values.

### Mixed-Integer Programming -- MIPLIB 2017

Classes in `org.ojalgo.benchmark.integer.miplib2017` benchmark the easy/benchmark subset of [MIPLIB 2017](https://miplib.zib.de/). Includes staged benchmarks: parse files, solve relaxed LP, find feasible MIP solutions, and find optimal MIP solutions.

## Solvers compared

ojAlgo's built-in solvers are compared against integrations with:

| Solver | Type |
|--------|------|
| [HiGHS](https://highs.dev/) | Open source LP/MIP |
| [Clarabel](https://github.com/oxfordcontrol/Clarabel.java) | Open source QP/conic |
| [Hipparchus](https://hipparchus.org/) | Open source (Apache Commons Math successor) |
| [JOptimizer](http://www.joptimizer.com/) | Open source QP |
| [OR-Tools](https://developers.google.com/optimization) | Google's optimisation suite |
| [CPLEX](https://www.ibm.com/products/ilog-cplex-optimization-studio) | Commercial LP/MIP/QP |
| [Gurobi](https://www.gurobi.com/) | Commercial LP/MIP/QP |
| [Mosek](https://www.mosek.com/) | Commercial conic/LP/MIP |

## How times are measured

Each model is parsed and pre-solved (`ExpressionsBasedModel.simplify()`) once, outside the timed region. Every solver gets that same pre-solved model.

The time is end-to-end through ojAlgo's integration with the solver - `ExpressionsBasedModel.minimise(integration)` or `maximise(integration)`. That includes translating the model to the solver's own API, any per-solve setup (creating the solver instance or problem object), the solve itself, and reading the solution back. It is the time it takes to solve a model using that solver from Java, the way optimisation-service-server does it, rather than the time the solver reports for its own algorithm. On models that solve in well under a millisecond those fixed per-solve costs dominate, and the comparison says more about the integration than about the algorithm.

The measurements are steady state. Solves run in worker JVMs that are reused from one model/solver pair to the next - JIT-compiled code, loaded native libraries and cached native environments stay in place between solves. A worker is only restarted after a timeout or a failure. Each pair is solved repeatedly until the last three times agree - the slowest and the fastest within 10% of the middle one - and that middle time is reported. Earlier times are dropped, so one-off costs such as class loading, JIT warm-up, library loading and environment creation are not part of the result, and neither are times from before the CPU had heated up. The middle rather than the fastest, so that the occasional lucky run of a solver that isn't deterministic doesn't decide the result either.

## Building and running

```sh
mvn clean install
```

Each benchmark has a `main` method. For example:

```sh
java -cp target/ojmpb.jar org.ojalgo.benchmark.linear.netlib.NetlibBenchmark
java -cp target/ojmpb.jar org.ojalgo.benchmark.convex.marosmeszaros.MarosMeszarosBenchmark
java -cp target/ojmpb.jar org.ojalgo.benchmark.integer.miplib2017.OptimalMIP
```
