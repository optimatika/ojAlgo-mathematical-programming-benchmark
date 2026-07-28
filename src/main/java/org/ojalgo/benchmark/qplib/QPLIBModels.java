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
package org.ojalgo.benchmark.qplib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.ojalgo.netio.EnumeratedColumnsParser;
import org.ojalgo.netio.EnumeratedColumnsParser.LineView;

/**
 * Model metadata extracted from the QPLIB instancedata.csv file.
 * <p>
 * The 3-letter problem type code encodes: objective type (L=linear, D=diagonal quadratic, C=convex quadratic,
 * Q=non-convex quadratic), variable type (C=continuous, B=binary, M=mixed-integer, I=general integer), and
 * constraint type (L=linear, C=convex quadratic, Q=non-convex quadratic).
 */
public final class QPLIBModels {

    enum Column {
        name, solsource, donor, nvars, ncons, nbinvars, nintvars, nsemi, nnlvars, nnlbinvars, nnlintvars, nnlsemi, nboundedvars, nsingleboundedvars, nsos1,
        nsos2, objsense, nobjnz, nobjnlnz, njacobiannz, njacobiannlnz, nlaghessiannz, nlaghessiandiagnz, nobjquadnz, nobjquaddiagnz, nobjquadnegev,
        nobjquadposev, objtype, objcurvature, conscurvature, nconvexnlcons, nconcavenlcons, nindefinitenlcons, nlincons, nquadcons, ndiagquadcons,
        nlaghessianblocks, laghessianminblocksize, laghessianmaxblocksize, laghessianavgblocksize, solobjvalue, solinfeasibility, probtype, nlinfunc,
        nquadfunc, nnlfunc, nz, nlnz, ncontvars, convex, density, nldensity, objquaddensity, objquadproblevfrac
    }

    public static final class ModelInfo {

        public final boolean convex;
        public final int nbinvars;
        public final int ncons;
        public final int ncontvars;
        public final int nintvars;
        public final int nvars;
        public final int nz;
        public final String probtype;
        public final BigDecimal solobjvalue;

        ModelInfo(final LineView line) {
            nvars = (int) line.intValue(Column.nvars);
            ncons = (int) line.intValue(Column.ncons);
            nbinvars = (int) line.intValue(Column.nbinvars);
            nintvars = (int) line.intValue(Column.nintvars);
            ncontvars = (int) line.intValue(Column.ncontvars);
            nz = (int) line.intValue(Column.nz);
            probtype = line.get(Column.probtype);
            convex = "True".equals(line.get(Column.convex));
            solobjvalue = line.toBigDecimal(Column.solobjvalue);
        }

        public boolean isContinuous() {
            return probtype.charAt(1) == 'C';
        }

        public boolean isConvex() {
            return convex;
        }

        public boolean isLinearlyConstrained() {
            return probtype.charAt(2) == 'L';
        }

        public boolean isLP() {
            return probtype.charAt(0) == 'L' && this.isContinuous() && this.isLinearlyConstrained();
        }

        public boolean isMIP() {
            char v = probtype.charAt(1);
            return v == 'B' || v == 'M' || v == 'I';
        }

        public boolean isQP() {
            char o = probtype.charAt(0);
            return o == 'Q' || o == 'D' || o == 'C';
        }

        @Override
        public String toString() {
            return "ModelInfo [nvars=" + nvars + ", ncons=" + ncons + ", probtype=" + probtype + ", convex=" + convex + ", solobjvalue=" + solobjvalue + "]";
        }
    }

    private static final Map<String, ModelInfo> MODEL_INFO;

    static {

        Map<String, ModelInfo> modelInfo = new HashMap<>();

        EnumeratedColumnsParser parser = EnumeratedColumnsParser.make(Column.class).get();

        InputStream resource = getResource("optimisation/QPLIB/instancedata.csv");
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource));

        parser.parse(() -> {
            try {
                return reader.readLine();
            } catch (IOException cause) {
                throw new RuntimeException(cause);
            }
        }, true, line -> {
            String name = line.get(Column.name);
            if (name != null && !name.isEmpty()) {
                modelInfo.put(name, new ModelInfo(line));
            }
        });

        MODEL_INFO = Collections.unmodifiableMap(modelInfo);
    }

    public static Map<String, ModelInfo> getModelInfo() {
        return MODEL_INFO;
    }

    public static ModelInfo getModelInfo(final String model) {
        return MODEL_INFO.get(model);
    }

    static InputStream getResource(final String path) {
        InputStream input = QPLIBModels.class.getResourceAsStream("/" + path);
        if (input == null) {
            input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }
        return input;
    }
}
