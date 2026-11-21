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

import org.ojalgo.benchmark.AbstractBenchmark;
import org.ojalgo.benchmark.Contender;

/**
 * ojAlgo's MIP solver, as configured by default, with and without bound propagation when branching – on a
 * subset of the {@link #EASY_SET}.
 * <p>
 * The subset: models ojAlgo's MIP solver (dual simplex, dense or sparse) has solved in 0.2 s to about 2 min,
 * in the runs of 2026-11-13 and 2026-11-21, including those solved in only one of those runs. Left out are
 * the trivial ones, the ones that took longer, and the ones listed as {@link #UNSTABLE}.
 */
public final class MIPLIBPropagation extends AbstractMIPLIB {

    static final Set<String> SUBSET = Set.of("23588", "bell3a", "bell3b", "bell5", "bienst1", "blend2", "bppc8-02", "dcmulti", "egout", "enigma",
            "enlight8", "enlight_hard", "markshare_4_0", "mas76", "mik-250-20-75-1", "mik-250-20-75-4", "mik-250-20-75-5", "misc05", "misc07", "mod008",
            "neos-3611447-jijia", "neos-3611689-kaihu", "p0201", "pigeon-08", "pk1", "pp08aCUTS", "rgn", "stein27", "stein45");

    public static void main(final String[] args) {

        Configuration configuration = AbstractMIPLIB.newConfiguration(Contender.OJALGO_MIP_BP_NONE, Contender.OJALGO_MIP_BP_BOTH);

        configuration.models.retainAll(SUBSET);

        configuration.maxIterations = 1;
        configuration.maxWaitTime = 3L * 60L * 1_000L;

        AbstractBenchmark.doBenchmark(configuration);
    }

}
