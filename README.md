# ojAlgo Mathematical Programming Benchmark (ojMPB)

Benchmarks for [ojAlgo](https://github.com/optimatika/ojAlgo) mathematical programming solvers, comparing against alternative Java and native solver integrations. Uses standard optimisation problem test sets and custom benchmark harnesses.

## Problem test sets

All model files, and the metadata that comes with them, are provided by the `optimisation-models` artifact. Models with more than 10k variables or constraints are listed in each set's index but not shipped.

| Set | Type | Package | Expected values from |
|-----|------|---------|----------------------|
| [Netlib](https://www.netlib.org/lp/data/) | LP | `org.ojalgo.benchmark.linear.netlib` | `NETLIB.solu` |
| [Burkardt](https://people.sc.fsu.edu/~jburkardt/datasets/mps/mps.html) | LP | `org.ojalgo.benchmark.linear.burkardt` | - |
| Meszaros | LP | `org.ojalgo.benchmark.linear.meszaros` | - |
| [Maros-Meszaros](https://www.doc.ic.ac.uk/~im/) | Convex QP | `org.ojalgo.benchmark.convex.marosmeszaros` | `00README.CSV` |
| [QPLIB](https://qplib.zib.de/) | QP/QCQP, continuous and integer | `org.ojalgo.benchmark.qplib` | `instancedata.csv` |
| [MIPLIB](https://miplib.zib.de/) | MIP | `org.ojalgo.benchmark.integer.miplib` | `miplib.solu` |

Maros-Meszaros and QPLIB also have metadata files describing each model (problem type, convexity, size...) that models can be filtered on.

## Benchmark classes

Each set has a `BenchmarkXYZ` class with its standard configuration. Only Netlib, Maros-Meszaros and MIPLIB are used for published results:

| Class | Published | Models | Solvers |
|-------|-----------|--------|---------|
| `BenchmarkNetlib` | Yes | All, up to 10k | ojAlgo-LP, Hipparchus, SCIP, HiGHS |
| `BenchmarkMarosMeszaros` | Yes | Pure QP, up to 10k | ojAlgo-QP, Hipparchus, SCIP, Clarabel |
| `BenchmarkMIPLIB` | Yes | The "easy set" - 103 models up to 1k | ojAlgo-MIP, SSC-LP, SCIP, HiGHS |
| `BenchmarkBurkardt` | No | All, up to 10k | ojAlgo-LP, Hipparchus, SCIP, HiGHS |
| `BenchmarkMeszaros` | No | All, up to 10k | ojAlgo-LP, Hipparchus, SCIP, HiGHS |
| `BenchmarkQPLIB` | No | Convex, continuous, linearly constrained QP, up to 10k | ojAlgo-QP, Hipparchus, Clarabel, HiGHS |

Each line-up is ojAlgo, the best Java (open source) alternative, the best open source native alternative, and one more.

Published results are always produced with a single worker - `configuration.parallelism = Parallelism.ONE`. More workers are only used to get results faster. Published results are kept under `results/<year>/<month>/`.

## Solvers compared

ojAlgo's built-in solvers are compared against integrations with:

| Solver | Type |
|--------|------|
| [Apache Commons Math](https://commons.apache.org/proper/commons-math/) (ACM) | Open source Java LP |
| [Hipparchus](https://hipparchus.org/) | Open source Java LP/QP (Apache Commons Math successor) |
| [JOptimizer](http://www.joptimizer.com/) | Open source Java QP |
| SSC-LP | Open source Java LP/MIP |
| [Choco](https://choco-solver.org/) | Open source Java constraint programming (integer models) |
| [Clarabel](https://github.com/oxfordcontrol/Clarabel.java) | Open source QP/conic |
| [HiGHS](https://highs.dev/) | Open source LP/MIP/QP |
| [OSQP](https://osqp.org/) | Open source QP |
| [SCIP](https://www.scipopt.org/) | Open source MIP/MINLP |
| [OR-Tools](https://developers.google.com/optimization) | Google's optimisation suite - not included in any benchmark by default |
| [CP-SAT](https://developers.google.com/optimization/cp/cp_solver) | OR-Tools' constraint programming solver |
| [COPT](https://www.shanshu.ai/copt) | Commercial LP/MIP/QP/conic |
| [CPLEX](https://www.ibm.com/products/ilog-cplex-optimization-studio) | Commercial LP/MIP/QP |
| [Gurobi](https://www.gurobi.com/) | Commercial LP/MIP/QP |
| [Mosek](https://www.mosek.com/) | Commercial conic/LP/MIP |
| [Xpress](https://www.fico.com/en/products/fico-xpress-optimization) | Commercial LP/MIP/QP |

Each solver, and each ojAlgo configuration (dense/sparse, primal/dual, QP algorithm...), is registered by name in `Contender`.

## How times are measured

Each model is parsed and pre-solved (`ExpressionsBasedModel.simplify()`) once, outside the timed region. Every solver gets that same pre-solved model.

The time is end-to-end through ojAlgo's integration with the solver - `ExpressionsBasedModel.minimise(integration)` or `maximise(integration)`. That includes translating the model to the solver's own API, any per-solve setup (creating the solver instance or problem object), the solve itself, and reading the solution back. It is the time it takes to solve a model using that solver from Java, the way optimisation-service-server does it, rather than the time the solver reports for its own algorithm. On models that solve in well under a millisecond those fixed per-solve costs dominate, and the comparison says more about the integration than about the algorithm.

The measurements are steady state. Solves run in worker JVMs that are reused from one model/solver pair to the next - JIT-compiled code, loaded native libraries and cached native environments stay in place between solves. A worker is only restarted after a timeout or a failure. Each pair is solved repeatedly until the last three times agree - the slowest and the fastest within 10% of the middle one - and that middle time is reported. Earlier times are dropped, so one-off costs such as class loading, JIT warm-up, library loading and environment creation are not part of the result, and neither are times from before the CPU had heated up. The middle rather than the fastest, so that the occasional lucky run of a solver that isn't deterministic doesn't decide the result either.

With `maxIterations = 1` each pair is solved exactly once. That only answers which solvers can solve which models - the times are single samples.

## How results are checked

A model counts as solved only if the solver:

- reports OPTIMAL,
- gives the same state and value every time it is solved,
- gives the expected value (to 4 significant digits), and
- returns a solution that actually satisfies the model's constraints.

The expected values come from the model set's metadata. A model without one is solved once with a reference solver before the benchmark starts, and that value is used instead - provided it is OPTIMAL and feasible:

- CPLEX, if the model is within the limits of its Community Edition - at most 1k variables and 1k constraints
- otherwise SCIP for MIP, Clarabel for QP and HiGHS for LP

This is skipped when `maxIterations` is 1. A model that still has no expected value is only checked to be OPTIMAL and feasible.

A pair that fails is reported with one of these reasons:

| Reason | Meaning |
|--------|---------|
| `FAILED` | Unexpected error/exception |
| `INVALID` | The solution does not satisfy the constraints, whatever its objective value |
| `TIMEOUT` | Hangs or takes too long |
| `UNSTABLE` | Not the same result every time |
| `WRONG` | Not the expected value, or not OPTIMAL (infeasible, unbounded, only feasible...) |

## Code structure

Each model set has its own package with:

- `AbstractXYZ` - everything specific to that set. A factory, `newConfiguration(String... solvers)`, that returns a `Configuration` with the full set of models, the file paths and the expected values. Where the set has a metadata file, also `filter(configuration, predicate)`.
- `BenchmarkXYZ` - the standard configuration for that set - for Netlib, Maros-Meszaros and MIPLIB, the one used for published results.
- Other subclasses - experiments.

The three published sets each have the same three experiments:

| Class | Compares | Netlib | Maros-Meszaros | MIPLIB |
|-------|----------|--------|----------------|--------|
| `XYZ4oj` | ojAlgo configurations | ojAlgo-LP primal/dual, dense/sparse | ojAlgo-QP ADMM and active set variants | ojAlgo-MIP primal/dual, dense/sparse |
| `XYZJavaSolvers` | Pure Java solvers - the best Java alternative, with ojAlgo as baseline | ACM, Hipparchus, JOptimizer, SSC-LP | Hipparchus, JOptimizer | SSC-LP, Choco |
| `XYZNativeSolvers` | All other solvers - the best over all, without ojAlgo | HiGHS, SCIP, CPLEX, Gurobi, COPT, Xpress, Mosek, Clarabel | Clarabel, OSQP, HiGHS, SCIP, CPLEX, Gurobi, COPT, Xpress, Mosek | Gurobi, CPLEX, Xpress, COPT, SCIP, HiGHS, Mosek, CP-SAT |

For Netlib and Maros-Meszaros they use the same models as the published benchmark. For MIPLIB only `BenchmarkMIPLIB` uses the "easy set" - the other MIPLIB classes use the full set with their own size limits. `MIPLIBNativeSolvers` is what defines the easy set: the models up to 1k that all of COPT, CPLEX, HiGHS, SCIP and Xpress solve.

The models to run are normally selected by filtering the full set:

- on size - `minProbSize` and `maxProbSize`, checked on the parsed model. By default 1 to 10k variables and at most 10k constraints - 1k for MIPLIB.
- on properties from the metadata, where there is any - `AbstractQPLIB.filter(configuration, info -> info.isConvex())`.

An explicit list of models is also possible, with `configuration.models.retainAll(...)` - `AbstractMIPLIB.EASY_SET` is one.

A typical `main`:

```java
Configuration configuration = AbstractQPLIB.newConfiguration(Contender.OJALGO_QP, Contender.HIPPARCHUS, Contender.CLARABEL, Contender.HIGHS);
AbstractQPLIB.filter(configuration, info -> info.isContinuous() && info.isConvex() && info.isLinearlyConstrained() && info.isQP());
configuration.parallelism = Parallelism.ONE;
AbstractBenchmark.doBenchmark(configuration);
```

Only the solver's name is passed to the worker JVMs, so a new solver configuration has to be registered in `Contender` - it cannot be defined in the experiment itself.

## Output files

Each run writes two files to `./src/main/resources/`, named after the class whose `main` method ran it, then `configuration.label` (if set), then the number of workers:

- `<MainClass>[_<label>]_<workers>_output.csv` - the results, one line per model/solver pair
- `<MainClass>[_<label>]_<workers>_console.log` - everything logged during the run

For example `BenchmarkNetlib_1_output.csv`. Use `label` to keep different runs of the same class apart - different solver builds, different size ranges...

## Native solvers

Where the native solvers are installed is set in `native-solvers.properties`, in the project root - edit it to match your own setup. Nothing solver specific needs to be in the launch configuration.

```properties
CPLEX.library.path=/Applications/CPLEX_Studio_Community222/cplex/bin/arm64_osx
Xpress.library.path=/Applications/FICO Xpress/xpressmp/lib
Xpress.env.XPRESSDIR=/Applications/FICO Xpress/xpressmp
```

- `<Solver>.library.path` - added to `java.library.path` of the worker JVMs, ahead of the main JVM's own
- `<Solver>.env.<NAME>` - an environment variable set for the worker JVMs

The settings are applied to the worker JVMs that do the solving, and logged at the start of every run. The main JVM needs none of them. Solvers that are not installed can be left out.

- CPLEX needs the installation matching the `cplex` jar in the pom - 22.2.0.0. A different version fails with an `UnsatisfiedLinkError` (`CPXopenCPLEX`). Without CPLEX the reference values for the smaller models are missing, and those models are only checked to be OPTIMAL and feasible.
- Gurobi's native libraries come with its jar - only the licence is needed.
- HiGHS and SCIP find an installed library themselves. To use a specific build instead, set its path in `configuration.libraries`, and give the run a `label`.
- Clarabel, OSQP and the pure Java solvers need nothing.

## Building and running

```sh
mvn clean install
```

Each benchmark has a `main` method. Run from the project root, since that is where the output files are written relative to. For example:

```sh
java -cp target/ojmpb.jar org.ojalgo.benchmark.linear.netlib.BenchmarkNetlib
java -cp target/ojmpb.jar org.ojalgo.benchmark.convex.marosmeszaros.BenchmarkMarosMeszaros
java -cp target/ojmpb.jar org.ojalgo.benchmark.integer.miplib.BenchmarkMIPLIB
```
