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

/**
 * The 94 MIPLIB models that ojAlgo, SCIP and HiGHS all solved within the timeout in
 * {@code org.ojalgo.optimisation.integer.MIPLIBTheEasySet}.
 */
public final class MIPLIBTheEasySet extends AbstractMIPLIB {

    public static void main(final String[] args) {

        Configuration configuration = new Configuration(Contender.OJALGO_MIP, Contender.SCIP, Contender.HIGHS, Contender.SSCLP);

        configuration.investigate = Set.of("22433", "23588", "aflow30a", "air01", "beavma", "bell3a", "bell3b", "bell4", "bell5", "bienst1", "blend2", "bm23",
                "bppc8-02", "cracpb1", "dcmulti", "egout", "enigma", "enlight_hard", "enlight8", "exp-1-500-5-5", "f2gap40400", "fixnet3", "fixnet4", "fixnet6",
                "flugpl", "gen", "gr4x6", "graphdraw-gemcutter", "gsvm2rl3", "gt2", "ic97_tension", "lseu", "mas76", "mik-250-1-100-1", "mik-250-20-75-1",
                "mik-250-20-75-2", "mik-250-20-75-3", "mik-250-20-75-4", "mik-250-20-75-5", "misc01", "misc02", "misc03", "misc05", "misc07", "mod008",
                "mod013", "modglob", "neos-1425699", "neos-2624317-amur", "neos-3610040-iskar", "neos-3610051-istra", "neos-3610173-itata",
                "neos-3611447-jijia", "neos-3611689-kaihu", "neos-5192052-neckar", "neos17", "nexp-50-20-1-1", "noswot", "opt1217", "p0033", "p0040", "p0201",
                "p0282", "p0291", "p0548", "pigeon-08", "pipex", "pk1", "pp08a", "pp08aCUTS", "prod1", "prod2", "r50x360", "ran12x21", "ran13x13", "ran16x16",
                "rgn", "rout", "sample2", "sentoy", "set1al", "set1ch", "set1cl", "sp150x300d", "stein15", "stein27", "stein45", "stein9", "supportcase14",
                "supportcase16", "timtab1", "timtab1CUTS", "vpm1", "vpm2");

        configuration.pathPrefix = "/optimisation/MIPLIB/";
        configuration.pathSuffix = ".mps";
        configuration.refeenceSolver = null;
        configuration.parallelism = Parallelism.FOUR;
        configuration.maxProbSize = 10_000;

        AbstractMIPLIB.loadExpectedValues(configuration);

        AbstractMIPLIB.doBenchmark(configuration);
    }

}
