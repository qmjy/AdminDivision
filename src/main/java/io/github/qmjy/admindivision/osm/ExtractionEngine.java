package io.github.qmjy.admindivision.osm;

import io.github.qmjy.admindivision.ExportOptions;
import io.github.qmjy.admindivision.export.GeoJsonWriter;
import io.github.qmjy.admindivision.geo.GeometryOps;
import io.github.qmjy.admindivision.geo.RingAssembler;
import io.github.qmjy.admindivision.geo.VisvalingamSimplifier;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orchestrates the extraction: three streaming passes over the PBF file
 * (relations -> ways -> nodes), ring assembly, optional simplification and
 * GeoJSON export.
 *
 * The three-pass design keeps memory bounded: only coordinates of nodes that
 * belong to boundary member ways are retained, instead of the whole planet's
 * node table.
 */
public final class ExtractionEngine {

    private final ExportOptions opts;
    private final Set<Integer> adminLevels = new TreeSet<>();
    private final Set<String> boundaryTypes = new LinkedHashSet<>();
    private double[] bboxFilter;

    public ExtractionEngine(ExportOptions opts) {
        this.opts = opts;
        parseFilters();
    }

    public void run() throws IOException, InterruptedException {
        File input = new File(opts.getInput());
        if (!input.isFile()) {
            throw new IOException("Input file not found: " + input.getAbsolutePath());
        }
        String output = opts.getOutput() != null ? opts.getOutput()
                : opts.getInput().replaceAll("(?i)\\.osm\\.pbf$", "") + ".geojson";

        log("input : " + input.getAbsolutePath());
        log("output: " + output);
        log("pass 1/3: scanning relations ...");

        // ------------------------------------------------------------------
        // Pass 1: relations -> boundary relations + needed way ids + hierarchy
        // ------------------------------------------------------------------
        Map<Long, BoundaryRelation> relations = new LinkedHashMap<>();
        LongOpenHashSet neededWays = new LongOpenHashSet();
        Map<Long, Set<Long>> parentsOf = new LinkedHashMap<>();

        OsmPbfScanner.scan(input,
                node -> { },
                way -> { },
                relation -> {
                    Map<String, String> tags = tagMap(relation);
                    String boundary = tags.get("boundary");
                    if (boundary == null) {
                        return;
                    }
                    BoundaryRelation br = new BoundaryRelation(relation.getId(), boundary,
                            tags.get("admin_level"), tags);
                    relation.getMembers().forEach(br::addMember);
                    if (!br.hasPolygonMembers()) {
                        return;
                    }
                    relations.put(br.id, br);
                    br.outerWayIds.forEach(neededWays::add);
                    br.innerWayIds.forEach(neededWays::add);
                    // hierarchy: role=subarea members point from parent to child
                    relation.getMembers().stream()
                            .filter(m -> m.getMemberType() == org.openstreetmap.osmosis.core.domain.v0_6.EntityType.Relation
                                    && "subarea".equals(m.getMemberRole()))
                            .forEach(m -> parentsOf
                                    .computeIfAbsent(m.getMemberId(), k -> new LinkedHashSet<>())
                                    .add(relation.getId()));
                });

        log("found " + relations.size() + " boundary relations");

        // ------------------------------------------------------------------
        // Pass 2: ways -> node id lists for member ways
        // ------------------------------------------------------------------
        log("pass 2/3: scanning ways ...");
        Long2ObjectOpenHashMap<long[]> wayNodes = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet neededNodes = new LongOpenHashSet();

        OsmPbfScanner.scan(input,
                node -> { },
                way -> {
                    if (neededWays.contains(way.getId())) {
                        List<org.openstreetmap.osmosis.core.domain.v0_6.WayNode> wns = way.getWayNodes();
                        long[] ids = new long[wns.size()];
                        for (int i = 0; i < ids.length; i++) {
                            ids[i] = wns.get(i).getNodeId();
                            neededNodes.add(ids[i]);
                        }
                        wayNodes.put(way.getId(), ids);
                    }
                },
                relation -> { });

        // ------------------------------------------------------------------
        // Pass 3: nodes -> coordinates for the needed nodes only
        // ------------------------------------------------------------------
        log("pass 3/3: scanning nodes (" + neededNodes.size() + " needed) ...");
        Long2DoubleOpenHashMap lons = new Long2DoubleOpenHashMap();
        Long2DoubleOpenHashMap lats = new Long2DoubleOpenHashMap();

        OsmPbfScanner.scan(input,
                node -> {
                    if (neededNodes.contains(node.getId())) {
                        lons.put(node.getId(), node.getLongitude());
                        lats.put(node.getId(), node.getLatitude());
                    }
                },
                way -> { },
                relation -> { });

        // ------------------------------------------------------------------
        // Build features and export
        // ------------------------------------------------------------------
        double simplify = opts.getSimplify();
        double simplifyThreshold = simplify > 0 ? simplify * simplify : 0;

        int written = 0;
        int skipped = 0;
        try (GeoJsonWriter writer = new GeoJsonWriter(output, opts.getPrecision())) {
            for (BoundaryRelation br : relations.values()) {
                List<String> warnings = new ArrayList<>();
                try {
                    if (!typeSelected(br.boundary) || !adminLevelSelected(br.adminLevel)) {
                        continue;
                    }

                    List<double[][]> outerRings = buildRings(br.outerWayIds, wayNodes, lons, lats, warnings);
                    if (outerRings.isEmpty()) {
                        skipped++;
                        warn(br, warnings);
                        continue;
                    }
                    List<double[][]> innerRings = buildRings(br.innerWayIds, wayNodes, lons, lats, warnings);

                    if (bboxFilter != null) {
                        double[] bbox = GeometryOps.bbox(outerRings);
                        if (!GeometryOps.bboxIntersects(bbox, bboxFilter)) {
                            continue;
                        }
                    }

                    // orientation: outer CCW, inner CW (GeoJSON RFC 7946)
                    for (double[][] ring : outerRings) {
                        if (GeometryOps.signedArea(ring) < 0) {
                            reverse(ring);
                        }
                    }
                    for (double[][] ring : innerRings) {
                        if (GeometryOps.signedArea(ring) > 0) {
                            reverse(ring);
                        }
                    }

                    // simplification
                    if (simplifyThreshold > 0) {
                        outerRings = simplifyAll(outerRings, simplifyThreshold);
                        innerRings = simplifyAll(innerRings, simplifyThreshold);
                    }

                    // assign holes to the smallest containing outer ring
                    List<List<double[][]>> polygons = new ArrayList<>();
                    for (double[][] outer : outerRings) {
                        List<double[][]> poly = new ArrayList<>();
                        poly.add(outer);
                        polygons.add(poly);
                    }
                    for (double[][] hole : innerRings) {
                        double[] p = hole[0];
                        int best = -1;
                        double bestArea = Double.MAX_VALUE;
                        for (int i = 0; i < outerRings.size(); i++) {
                            if (GeometryOps.pointInRing(p[0], p[1], outerRings.get(i))) {
                                double a = Math.abs(GeometryOps.signedArea(outerRings.get(i)));
                                if (a < bestArea) {
                                    bestArea = a;
                                    best = i;
                                }
                            }
                        }
                        if (best >= 0) {
                            polygons.get(best).add(hole);
                        } else {
                            warnings.add("inner ring could not be assigned to an outer ring");
                        }
                    }

                    writer.writeFeature(br.id, br.boundary, parseIntOrNull(br.adminLevel),
                            parentsChain(br.id, parentsOf), displayName(br), localName(br), nameEn(br),
                            opts.isIncludeTags() ? br.tags : null, polygons);
                    written++;
                } finally {
                    if (!warnings.isEmpty()) {
                        warn(br, warnings);
                    }
                }
            }
        }

        log("done: " + written + " features written, " + skipped + " relations skipped (incomplete geometry)");
        log("output written to " + output);
    }

    // ---------------------------------------------------------------------

    private List<double[][]> buildRings(LongOpenHashSet wayIds,
                                        Long2ObjectOpenHashMap<long[]> wayNodes,
                                        Long2DoubleOpenHashMap lons,
                                        Long2DoubleOpenHashMap lats,
                                        List<String> warnings) {
        List<long[]> ways = new ArrayList<>();
        for (long wayId : wayIds) {
            long[] nodes = wayNodes.get(wayId);
            if (nodes == null) {
                warnings.add("missing member way " + wayId);
                continue;
            }
            boolean complete = true;
            for (long n : nodes) {
                if (!lons.containsKey(n)) {
                    warnings.add("missing node " + n + " in way " + wayId);
                    complete = false;
                    break;
                }
            }
            if (complete) {
                ways.add(nodes);
            }
        }
        List<long[]> nodeRings = RingAssembler.assemble(ways, warnings);

        List<double[][]> coordRings = new ArrayList<>(nodeRings.size());
        for (long[] ring : nodeRings) {
            double[][] coords = new double[ring.length - 1][];
            for (int i = 0; i < coords.length; i++) {
                coords[i] = new double[]{lons.get(ring[i]), lats.get(ring[i])};
            }
            coordRings.add(coords);
        }
        return coordRings;
    }

    private static List<double[][]> simplifyAll(List<double[][]> rings, double threshold) {
        List<double[][]> out = new ArrayList<>(rings.size());
        for (double[][] ring : rings) {
            double[][] simplified = VisvalingamSimplifier.simplifyRing(ring, threshold);
            if (simplified.length >= 4) {
                out.add(simplified);
            }
        }
        return out;
    }

    private static void reverse(double[][] ring) {
        for (int i = 0, j = ring.length - 1; i < j; i++, j--) {
            double[] tmp = ring[i];
            ring[i] = ring[j];
            ring[j] = tmp;
        }
    }

    private String parentsChain(long id, Map<Long, Set<Long>> parentsOf) {
        if (!opts.isHierarchy()) {
            return null;
        }
        List<String> chain = new ArrayList<>();
        Long current = id;
        int guard = 0;
        while (guard++ < 32) {
            Set<Long> parents = parentsOf.get(current);
            if (parents == null || parents.isEmpty()) {
                break;
            }
            long next = parents.iterator().next();
            chain.add("-" + next);
            current = next;
        }
        return chain.isEmpty() ? null : String.join(",", chain);
    }

    private static String displayName(BoundaryRelation br) {
        String nameEn = br.tags.get("name:en");
        String name = br.tags.get("name");
        return nameEn != null ? nameEn : name;
    }

    private static String localName(BoundaryRelation br) {
        return br.tags.get("name");
    }

    private static String nameEn(BoundaryRelation br) {
        return br.tags.get("name:en");
    }

    private boolean typeSelected(String boundary) {
        return boundaryTypes.isEmpty() || boundaryTypes.contains(boundary);
    }

    private boolean adminLevelSelected(String adminLevel) {
        if (adminLevels.isEmpty()) {
            return true;
        }
        Integer level = parseIntOrNull(adminLevel);
        return level != null && adminLevels.contains(level);
    }

    private void parseFilters() {
        if (opts.getAdminLevels() != null && !opts.getAdminLevels().isBlank()) {
            for (String part : opts.getAdminLevels().split(",")) {
                part = part.trim();
                if (part.isEmpty()) {
                    continue;
                }
                if (part.contains("-")) {
                    String[] range = part.split("-", 2);
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        adminLevels.add(i);
                    }
                } else {
                    adminLevels.add(Integer.parseInt(part));
                }
            }
        }
        if (opts.getBoundaryTypes() != null && !opts.getBoundaryTypes().isBlank()) {
            for (String part : opts.getBoundaryTypes().split(",")) {
                if (!part.isBlank()) {
                    boundaryTypes.add(part.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (opts.getBbox() != null && !opts.getBbox().isBlank()) {
            String[] parts = opts.getBbox().split(",");
            if (parts.length != 4) {
                throw new IllegalArgumentException("bbox must be minLon,minLat,maxLon,maxLat");
            }
            bboxFilter = new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim())
            };
        }
        if (opts.getSrid() != 4326) {
            throw new IllegalArgumentException("Only SRID 4326 (WGS84) is supported");
        }
    }

    private static Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, String> tagMap(Relation relation) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Tag tag : relation.getTags()) {
            map.put(tag.getKey(), tag.getValue());
        }
        return map;
    }

    private static Map<String, String> tagMap(Way way) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Tag tag : way.getTags()) {
            map.put(tag.getKey(), tag.getValue());
        }
        return map;
    }

    private void warn(BoundaryRelation br, List<String> warnings) {
        if (!opts.isVerbose()) {
            return;
        }
        System.err.println("[warn] relation " + br.id + " (" + br.tags.get("name") + "): "
                + warnings.size() + " issue(s)");
        for (String w : warnings) {
            System.err.println("       - " + w);
        }
    }

    private void log(String message) {
        System.err.println("[info] " + message);
    }
}
