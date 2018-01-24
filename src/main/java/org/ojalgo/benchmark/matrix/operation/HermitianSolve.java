/*
 * Copyright 1997-2017 Optimatika (www.optimatika.se)
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
package org.ojalgo.benchmark.matrix.operation;

import org.ojalgo.benchmark.BenchmarkRequirementsException;
import org.ojalgo.benchmark.matrix.MatrixBenchmarkLibrary;
import org.ojalgo.benchmark.matrix.MatrixBenchmarkOperation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.RunnerException;

/**
 * Mac Pro: 2015-06-13
 *
 * <pre>
 * </pre>
 *
 * MacBook Air: 2015-06-13
 *
 * <pre>
 * </pre>
 *
 * @author apete
 */
@State(Scope.Benchmark)
public class HermitianSolve extends MatrixBenchmarkOperation {

    @FunctionalInterface
    public static interface TaskDefinition<T> {

        int doThThing();

    }

    public static void main(final String[] args) throws RunnerException {
        MatrixBenchmarkOperation.run(HermitianSolve.class);
    }

    Object body;
    @Param({ "2", "3", "4", "5", "10", "20", "50", "100", "200", "500", "1000"/* , "2000", "5000", "10000" */ })
    public int dim;

    @Param({ "EJML", "MTJ", "ojAlgo" })
    public String library;

    private MatrixBenchmarkLibrary<?, ?>.HermitianSolver myHermitianSolver;
    Object rhs;

    @Override
    @Benchmark
    public Object execute() {
        return myHermitianSolver.solve(body, rhs);
    }

    @Setup
    public void setup() {

        contestant = MatrixBenchmarkLibrary.LIBRARIES.get(library);

        myHermitianSolver = contestant.getHermitianSolver();

        body = this.makeSPD(dim, contestant);
        rhs = this.makeRandom(dim, 1, contestant);
    }

    @Override
    @TearDown(Level.Iteration)
    public void verify() throws BenchmarkRequirementsException {

        this.verifyStateless(myHermitianSolver.getClass());

    }

}
