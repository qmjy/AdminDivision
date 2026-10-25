package io.github.qmjy.admindivision.geo;

import java.util.List;

/**
 * Small geometry helpers on plain {@code double[]} rings.
 */
public final class GeometryOps {

    private GeometryOps() {
    }

    /** Shoelace signed area; positive means counter-clockwise. */
    public static double signedArea(double[][] ring) {
        double sum = 0;
        for (int i = 0, n = ring.length; i < n; i++) {
            double[] a = ring[i];
            double[] b = ring[(i + 1) % n];
            sum += (a[0] * b[1] - b[0] * a[1]);
        }
        return sum / 2.0;
    }

    /** Even-odd point in ring test. Ring must be closed or the wrap-around handles it. */
    public static boolean pointInRing(double x, double y, double[][] ring) {
        boolean inside = false;
        int n = ring.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring[i][0], yi = ring[i][1];
            double xj = ring[j][0], yj = ring[j][1];
            if (((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** bbox: [minLon, minLat, maxLon, maxLat] of the given coordinates. */
    public static double[] bbox(List<double[][]> rings) {
        double minLon = Double.MAX_VALUE, minLat = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (double[][] ring : rings) {
            for (double[] p : ring) {
                if (p[0] < minLon) {
                    minLon = p[0];
                }
                if (p[1] < minLat) {
                    minLat = p[1];
                }
                if (p[0] > maxLon) {
                    maxLon = p[0];
                }
                if (p[1] > maxLat) {
                    maxLat = p[1];
                }
            }
        }
        return new double[]{minLon, minLat, maxLon, maxLat};
    }

    public static boolean bboxIntersects(double[] a, double[] b) {
        return a[0] <= b[2] && a[2] >= b[0] && a[1] <= b[3] && a[3] >= b[1];
    }
}
