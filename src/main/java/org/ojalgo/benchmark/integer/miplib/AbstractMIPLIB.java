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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Set;

import org.ojalgo.benchmark.AbstractBenchmark;

abstract class AbstractMIPLIB extends AbstractBenchmark {

    /**
     * The 103 MIPLIB models in ojAlgo's "easy set". Essentially these are models that have been in one (any)
     * of the MIPLIB editions, have no more than 1k variables or constraints and have been verified to be
     * solvable by each of COPT, CPLEX, HiGHS, SCIP and Xpress within this benchmark's timeout setting.
     */
    static final Set<String> EASY_SET = Set.of("22433", "23588", "aflow30a", "air01", "beavma", "bell3a", "bell3b", "bell4", "bell5", "bienst1", "bienst2",
            "blend2", "bm23", "bppc8-02", "cracpb1", "dcmulti", "dfn-gwin-UUM", "egout", "enigma", "enlight13", "enlight8", "enlight_hard", "exp-1-500-5-5",
            "f2gap40400", "fixnet3", "fixnet4", "fixnet6", "flugpl", "gen", "gen-ip021", "gen-ip036", "gr4x6", "graphdraw-gemcutter", "gt2", "ic97_tension",
            "lseu", "markshare_4_0", "mas76", "mik-250-1-100-1", "mik-250-20-75-1", "mik-250-20-75-2", "mik-250-20-75-3", "mik-250-20-75-4", "mik-250-20-75-5",
            "misc01", "misc02", "misc03", "misc05", "misc07", "mod008", "mod013", "modglob", "neos-1425699", "neos-1430701", "neos-2624317-amur",
            "neos-3610040-iskar", "neos-3610051-istra", "neos-3610173-itata", "neos-3611447-jijia", "neos-3611689-kaihu", "neos-5192052-neckar", "neos-911880",
            "neos-911970", "neos17", "neos5", "nexp-50-20-1-1", "noswot", "opt1217", "p0033", "p0040", "p0201", "p0282", "p0291", "p0548", "pigeon-08", "pipex",
            "pk1", "pp08a", "pp08aCUTS", "prod1", "prod2", "r50x360", "ran12x21", "ran13x13", "ran16x16", "rgn", "rout", "sample2", "sentoy", "set1al",
            "set1ch", "set1cl", "sp150x300d", "stein15", "stein27", "stein45", "stein9", "supportcase14", "supportcase16", "timtab1", "timtab1CUTS", "vpm1",
            "vpm2");

    private static final String RESOURCE_DIR = "optimisation/MIPLIB/";

    /**
     * All models listed in {@code MIPLIB.dat}, except those {@code miplib.solu} says are infeasible or
     * unbounded, with the optimal values from {@code miplib.solu} as expected values. Size limited to 1k
     * variables and constraints, rather than the usual 10k.
     */
    static Configuration newConfiguration(final String... solvers) {

        Configuration configuration = new Configuration(solvers);

        configuration.pathPrefix = "/" + RESOURCE_DIR;
        configuration.pathSuffix = ".mps";
        configuration.maxProbSize = 1_000;

        configuration.models.addAll(AbstractBenchmark.readIndex(RESOURCE_DIR + "MIPLIB.dat"));

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE_DIR + "miplib.solu")))) {

            String line;
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split("\\s+");

                if (parts.length >= 2 && ("=inf=".equals(parts[0]) || "=unbd=".equals(parts[0]))) {
                    configuration.models.remove(parts[1]);
                } else if (parts.length == 3 && "=opt=".equals(parts[0])) {
                    configuration.values.put(parts[1], new BigDecimal(parts[2]));
                }
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        return configuration;
    }

}
