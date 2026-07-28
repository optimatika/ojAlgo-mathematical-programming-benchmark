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
package org.ojalgo.benchmark.convex.marosmeszaros;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Model metadata extracted from the Maros-Meszaros QP test set's 00README.CSV file.
 */
public final class MarosMeszarosModels {

    public static final class ModelInfo {

        public int M;
        public int N;
        public int NZ;
        public BigDecimal OPT;
        public int QN;
        public int QNZ;

        public double getRatioQP() {
            return (double) QN / (double) N;
        }

        public boolean isPureQP() {
            return QN == N;
        }

        public boolean isSeparable() {
            return QNZ == 0;
        }

        public boolean isSmall() {
            return M <= 1_000 && N <= 1_000;
        }

        @Override
        public String toString() {
            return "ModelInfo [M=" + M + ", N=" + N + ", NZ=" + NZ + ", QN=" + QN + ", QNZ=" + QNZ + ", OPT=" + OPT + "]";
        }
    }

    private static final Map<String, ModelInfo> MODEL_INFO;

    static {

        Map<String, ModelInfo> modelInfo = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(MarosMeszarosModels.getResource("optimisation/marosmeszaros/00README.CSV")))) {

            String line;
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split("\\s+");

                String key = parts[0].toUpperCase();

                ModelInfo value = new ModelInfo();
                value.M = Integer.parseInt(parts[1]);
                value.N = Integer.parseInt(parts[2]);
                value.NZ = Integer.parseInt(parts[3]);
                value.QN = Integer.parseInt(parts[4]);
                value.QNZ = Integer.parseInt(parts[5]);
                value.OPT = new BigDecimal(parts[6]);

                modelInfo.put(key, value);
            }

        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }

        modelInfo.get("HS268").OPT = BigDecimal.ZERO;

        MODEL_INFO = Collections.unmodifiableMap(modelInfo);
    }

    public static Map<String, ModelInfo> getModelInfo() {
        return MODEL_INFO;
    }

    public static ModelInfo getModelInfo(final String model) {
        String key = model.toUpperCase();
        key = key.replace("_", "");
        int dotIndex = key.indexOf(".");
        if (dotIndex > 0) {
            key = key.substring(0, dotIndex);
        }
        return MODEL_INFO.get(key);
    }

    static InputStream getResource(final String path) {
        InputStream input = MarosMeszarosModels.class.getResourceAsStream("/" + path);
        if (input == null) {
            input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }
        return input;
    }
}
