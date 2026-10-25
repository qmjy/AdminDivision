package io.github.qmjy.admindivision;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Export control parameters, modelled after the download settings of
 * https://osm-boundaries.com/ .
 *
 * <ul>
 *   <li>adminLevels   – "Admin level filter"</li>
 *   <li>boundaryTypes – "Boundary filter" (value of the boundary=* tag)</li>
 *   <li>bbox          – geographic selection of the download</li>
 *   <li>simplify      – "Simplify" (Visvalingam-Whyatt factor in degrees)</li>
 *   <li>includeTags   – "Tags" (emit all_tags property)</li>
 *   <li>hierarchy     – "Hierarchy data" (emit parents property)</li>
 *   <li>srid          – "SRID" (only EPSG:4326 is implemented)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "export")
public class ExportOptions {

    /** Path of the source .osm.pbf file (required). */
    private String input;

    /** Path of the GeoJSON file to write. Defaults to the input name + ".geojson". */
    private String output;

    /**
     * Admin level filter, e.g. "4" or "4,6,8" or a range "2-10".
     * Empty or null keeps every admin_level.
     */
    private String adminLevels;

    /**
     * Boundary filter: comma separated values of the boundary=* tag,
     * e.g. "administrative,national_park". Empty keeps every boundary relation.
     */
    private String boundaryTypes;

    /**
     * Bounding box filter: "minLon,minLat,maxLon,maxLat" in degrees.
     * Empty keeps everything.
     */
    private String bbox;

    /**
     * Geometry simplification factor in degrees, using the Visvalingam-Whyatt
     * algorithm (same semantics as osm-boundaries.com "Simplify":
     * 0.001 ~ 100 m, 0.01 ~ 1 km, 0.1 ~ 10 km). 0 disables simplification.
     */
    private double simplify = 0;

    /** Include the all_tags property (osm-boundaries.com "Tags" option). */
    private boolean includeTags = true;

    /** Include the parents property (osm-boundaries.com "Hierarchy data" option). */
    private boolean hierarchy = true;

    /** Output SRID. Only EPSG:4326 (WGS84) is supported. */
    private int srid = 4326;

    /** Number of decimal digits kept for coordinates (OSMB uses 7). */
    private int precision = 7;

    /** Print progress and warning details to stderr. */
    private boolean verbose = true;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getAdminLevels() {
        return adminLevels;
    }

    public void setAdminLevels(String adminLevels) {
        this.adminLevels = adminLevels;
    }

    public String getBoundaryTypes() {
        return boundaryTypes;
    }

    public void setBoundaryTypes(String boundaryTypes) {
        this.boundaryTypes = boundaryTypes;
    }

    public String getBbox() {
        return bbox;
    }

    public void setBbox(String bbox) {
        this.bbox = bbox;
    }

    public double getSimplify() {
        return simplify;
    }

    public void setSimplify(double simplify) {
        this.simplify = simplify;
    }

    public boolean isIncludeTags() {
        return includeTags;
    }

    public void setIncludeTags(boolean includeTags) {
        this.includeTags = includeTags;
    }

    public boolean isHierarchy() {
        return hierarchy;
    }

    public void setHierarchy(boolean hierarchy) {
        this.hierarchy = hierarchy;
    }

    public int getSrid() {
        return srid;
    }

    public void setSrid(int srid) {
        this.srid = srid;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
