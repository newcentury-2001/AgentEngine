package com.agentengine.skill.preprocess.util;

import java.util.List;

public final class VectorUtils {

    private VectorUtils() {
    }

    public static double[] l2Normalize(double[] vector) {
        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            return vector.clone();
        }
        double[] out = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            out[i] = vector[i] / norm;
        }
        return out;
    }

    public static double[] weightedAverage(List<double[]> vectors, List<Double> weights) {
        if (vectors.isEmpty()) {
            return new double[0];
        }
        int dim = vectors.get(0).length;
        double[] out = new double[dim];
        double weightSum = 0.0;
        for (int i = 0; i < vectors.size(); i++) {
            double[] v = vectors.get(i);
            double w = weights.get(i);
            weightSum += w;
            for (int d = 0; d < dim; d++) {
                out[d] += v[d] * w;
            }
        }
        if (weightSum == 0.0) {
            return out;
        }
        for (int d = 0; d < dim; d++) {
            out[d] = out[d] / weightSum;
        }
        return out;
    }

    public static double[] blend(double[] a, double wa, double[] b, double wb) {
        int dim = a.length;
        double[] out = new double[dim];
        for (int i = 0; i < dim; i++) {
            out[i] = a[i] * wa + b[i] * wb;
        }
        return out;
    }
}
