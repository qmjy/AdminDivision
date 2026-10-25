package io.github.qmjy.admindivision.geo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles open OSM ways into closed rings by joining ways that share
 * endpoint nodes (classic multipolygon member-way stitching).
 */
public final class RingAssembler {

    private RingAssembler() {
    }

    /**
     * Joins the given ways (each a sequence of node ids) into closed rings.
     *
     * @param ways   list of ways, each way an array of node ids (open or closed)
     * @param out    sink that receives diagnostic warnings
     * @return list of closed rings; each ring is an array of node ids where
     *         the first id equals the last id
     */
    public static List<long[]> assemble(List<long[]> ways, List<String> out) {
        int n = ways.size();
        boolean[] used = new boolean[n];
        // endpoint node id -> indexes of ways having that endpoint
        Map<Long, List<Integer>> endpoint = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long[] w = ways.get(i);
            if (w.length < 2) {
                continue;
            }
            endpoint.computeIfAbsent(w[0], k -> new ArrayList<>()).add(i);
            endpoint.computeIfAbsent(w[w.length - 1], k -> new ArrayList<>()).add(i);
        }

        List<long[]> rings = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (used[i]) {
                continue;
            }
            long[] w = ways.get(i);
            if (w.length < 2) {
                out.add("skipping degenerate way with " + w.length + " nodes");
                used[i] = true;
                continue;
            }
            used[i] = true;
            removeEndpoints(endpoint, i, w);
            Deque<Long> chain = new ArrayDeque<>();
            for (long id : w) {
                chain.addLast(id);
            }

            // Extend the chain at both ends until closed or stuck.
            boolean extended = true;
            while (extended && !chain.getFirst().equals(chain.getLast())) {
                extended = false;
                long tail = chain.getLast();
                int candidate = takeCandidate(endpoint, used, tail);
                if (candidate >= 0) {
                    long[] cw = ways.get(candidate);
                    used[candidate] = true;
                    removeEndpoints(endpoint, candidate, cw);
                    appendAtTail(chain, cw, tail);
                    extended = true;
                    continue;
                }
                long head = chain.getFirst();
                candidate = takeCandidate(endpoint, used, head);
                if (candidate >= 0) {
                    long[] cw = ways.get(candidate);
                    used[candidate] = true;
                    removeEndpoints(endpoint, candidate, cw);
                    prependAtHead(chain, cw, head);
                    extended = true;
                }
            }

            if (chain.getFirst().equals(chain.getLast()) && chain.size() >= 4) {
                long[] ring = new long[chain.size()];
                int k = 0;
                for (long id : chain) {
                    ring[k++] = id;
                }
                rings.add(ring);
            } else {
                out.add("dropped incomplete ring built from " + chain.size() + " nodes");
            }
        }
        return rings;
    }

    private static int takeCandidate(Map<Long, List<Integer>> endpoint, boolean[] used, long nodeId) {
        List<Integer> candidates = endpoint.get(nodeId);
        if (candidates == null) {
            return -1;
        }
        while (!candidates.isEmpty()) {
            int idx = candidates.remove(candidates.size() - 1);
            if (!used[idx]) {
                return idx;
            }
        }
        return -1;
    }

    private static void removeEndpoints(Map<Long, List<Integer>> endpoint, int idx, long[] way) {
        removeFrom(endpoint, way[0], idx);
        removeFrom(endpoint, way[way.length - 1], idx);
    }

    private static void removeFrom(Map<Long, List<Integer>> endpoint, long nodeId, int idx) {
        List<Integer> list = endpoint.get(nodeId);
        if (list != null) {
            list.remove(Integer.valueOf(idx));
        }
    }

    private static void appendAtTail(Deque<Long> chain, long[] way, long tail) {
        boolean forward = way[0] == tail;
        if (forward) {
            for (int i = 1; i < way.length; i++) {
                chain.addLast(way[i]);
            }
        } else {
            for (int i = way.length - 2; i >= 0; i--) {
                chain.addLast(way[i]);
            }
        }
    }

    private static void prependAtHead(Deque<Long> chain, long[] way, long head) {
        boolean forward = way[way.length - 1] == head;
        if (forward) {
            for (int i = way.length - 2; i >= 0; i--) {
                chain.addFirst(way[i]);
            }
        } else {
            for (int i = 1; i < way.length; i++) {
                chain.addFirst(way[i]);
            }
        }
    }
}
