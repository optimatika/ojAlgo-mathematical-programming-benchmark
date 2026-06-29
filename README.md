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
