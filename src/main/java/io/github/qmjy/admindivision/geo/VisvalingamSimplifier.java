package io.github.qmjy.admindivision.geo;

import java.util.TreeSet;

/**
 * Visvalingam-Whyatt polyline simplification, the same algorithm that
 * osm-boundaries.com uses for its "Simplify" export option.
 *
 * The threshold is expressed in square degrees (the site expresses the
 * factor in degrees; a factor f corresponds to a triangle-area threshold of
 * f^2). Endpoints of the polyline are always kept; closed rings are handled
 * by the caller, which passes the ring without the duplicated closing point.
 */
public final class VisvalingamSimplifier {

    private VisvalingamSimplifier() {
    }

    /**
     * Simplifies an open polyline. Keeps first and last points.
     */
    public static double[][] simplify(double[][] pts, double threshold) {
        int n = pts.length;
        if (threshold <= 0 || n <= 4) {
            return pts;
        }

        final int[] prev = new int[n];
        final int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            prev[i] = i - 1;
            next[i] = i + 1;
        }
        prev[0] = 0;
        next[n - 1] = n - 1;

        final double[] area = new double[n];
        // Orders by current effective area; live comparator over area[].
        TreeSet<Integer> heap = new TreeSet<>((a, b) -> {
            int c = Double.compare(area[a], area[b]);
            return c != 0 ? c : Integer.compare(a, b);
        });

        for (int i = 1; i < n - 1; i++) {
            area[i] = effectiveArea(pts, prev[i], i, next[i]);
            heap.add(i);
        }

        while (!heap.isEmpty()) {
            int i = heap.pollFirst();
            if (area[i] >= threshold) {
                heap.add(i);
                break;
            }
            int p = prev[i];
            int nx = next[i];
            // unlink i
            next[p] = nx;
            prev[nx] = p;
            // refresh neighbours
            if (p != 0) {
                heap.remove(p);
                area[p] = effectiveArea(pts, prev[p], p, next[p]);
                heap.add(p);
            }
            if (nx != n - 1) {
                heap.remove(nx);
                area[nx] = effectiveArea(pts, prev[nx], nx, next[nx]);
                heap.add(nx);
            }
        }

        // Collect surviving vertices by walking the linked list.
        int count = 0;
        for (int i = 0; i != n - 1; i = next[i]) {
            count++;
        }
        count++; // the last point
        double[][] out = new double[count][];
        int k = 0;
        for (int i = 0; i != n - 1; i = next[i]) {
            out[k++] = pts[i];
        }
        out[k] = pts[n - 1];
        return out;
    }

    /**
     * Simplifies a closed ring (first point == last point) and re-closes it.
     */
    public static double[][] simplifyRing(double[][] ring, double threshold) {
        if (threshold <= 0 || ring.length <= 4) {
            return ring;
        }
        boolean closed = ring[0][0] == ring[ring.length - 1][0]
                && ring[0][1] == ring[ring.length - 1][1];
        double[][] open = closed ? java.util.Arrays.copyOfRange(ring, 0, ring.length - 1) : ring;
        double[][] simplified = simplify(open, threshold);
        if (simplified.length < 4) {
            return ring;
        }
        if (!closed) {
            return simplified;
        }
        double[][] out = new double[simplified.length + 1][];
        System.arraycopy(simplified, 0, out, 0, simplified.length);
        out[simplified.length] = out[0];
        return out;
    }

    private static double effectiveArea(double[][] pts, int a, int b, int c) {
        double[] p1 = pts[a], p2 = pts[b], p3 = pts[c];
        return Math.abs(
                (p1[0] - p3[0]) * (p2[1] - p1[1])
                        - (p1[0] - p2[0]) * (p3[1] - p1[1])) / 2.0;
    }
}
