/*
 * Copyright 1997-2026 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.benchmark.integer.miplib;

import java.util.Set;

import org.ojalgo.concurrent.Parallelism;
import org.ojalgo.netio.BasicLogger;

/**
 * Which MIPLIB models a given build of HiGHS and SCIP can solve.
 * <p>
 * optimisation-service compiles both solvers from source and ships them in its Docker image, but the
 * benchmark has only ever measured whatever happened to be installed on the machine, or whatever OR-Tools
 * bundled. Those are not the same libraries, and on at least one model they differ by more than 10x -
 * {@code neos-2624317-amur} takes OR-Tools' SCIP 28.5s and defeats the Homebrew build entirely.
 * <p>
 * One build of each solver per run. Set {@link #LABEL} and the two library paths, run, then change them and
 * run again - each run writes its own file, and the builds are compared by diffing those.
 * <p>
 * <b>The libraries must be built for the machine this runs on.</b> A Linux {@code .so} out of the Docker
 * image will not load on macOS - the Docker build has to be reproduced locally with the same cmake switches
 * (see the Dockerfile: {@code LPS=spx PAPILO=on SYM=snauty IPOPT=off EXPRINT=none}), or this has to run
 * inside a container built from the service image.
 */
public final class MIPLIBSolverBuilds extends AbstractMIPLIB {

    /**
     * Names the run, and therefore the output file. Nothing else records which build produced a result.
     */
    private static final String LABEL = "papilo-gate";

    /**
     * Absolute path to the library to use, or empty to take whatever the integration finds installed.
     */
    private static final String HIGHS_LIB = "";
    private static final String SCIP_LIB = "";

    /**
     * The four slowest models of the easy set, plus {@code neos-2624317-amur} - the one the Homebrew SCIP
     * cannot solve at all while OR-Tools' does it in under 30s. Slow models are where builds have room to
     * differ; the fast ones agree on everything and only add wall-clock.
     */
    private static final Set<String> MODELS = MIPLIBTheEasySet.MODELS;

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.HIGHS, Contender.SCIP);

        if (!HIGHS_LIB.isEmpty()) {
            configuration.libraries.put(Contender.HIGHS, HIGHS_LIB);
        }
        if (!SCIP_LIB.isEmpty()) {
            configuration.libraries.put(Contender.SCIP, SCIP_LIB);
        }

        configuration.investigate = MODELS;

        configuration.pathPrefix = "/optimisation/MIPLIB/";
        configuration.pathSuffix = ".mps";
        configuration.refeenceSolver = null;
        configuration.maxProbSize = 10_000;

        // Which models the build solves is the question - not how fast - so one pass each.
        configuration.maxIterations = 1;

        // Contention matters more here than elsewhere: four concurrent solves of r50x360 took it from 17s
        // to 35s, and that kind of slowdown can push a model past the timeout and record a build as failing
        // something it can actually do. Few workers, and a timeout with room to spare.
        configuration.parallelism = Parallelism.TWO;
        configuration.maxWaitTime = 1_000L * 60L * 5L;

        configuration.outputPath = "./src/main/resources/miplib_builds_" + LABEL + "_output.csv";

        BasicLogger.debug();
        BasicLogger.debug("Solver build: {}", LABEL);
        BasicLogger.debug("HiGHS: {}", HIGHS_LIB.isEmpty() ? "<installed>" : HIGHS_LIB);
        BasicLogger.debug("SCIP: {}", SCIP_LIB.isEmpty() ? "<installed>" : SCIP_LIB);

        AbstractMIPLIB.doBenchmark(configuration);
    }

}
