package io.github.qmjy.admindivision;

import io.github.qmjy.admindivision.osm.ExtractionEngine;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Command line entry point.
 *
 * <pre>
 * java -jar admin-division-extractor.jar china.osm.pbf \
 *      --output=china.geojson --admin-levels=4,6 --simplify=0.001
 * </pre>
 *
 * Options may also be given as Spring properties, e.g.
 * {@code --export.input=... --export.admin-levels=...}.
 */
@Component
public class BoundaryExtractorRunner implements ApplicationRunner {

    private static final String[] KNOWN_KEYS = {
            "input", "output", "admin-levels", "adminLevels",
            "boundary-types", "boundaryTypes", "bbox",
            "simplify", "include-tags", "includeTags",
            "hierarchy", "srid", "precision", "verbose"
    };

    private final ExportOptions options;

    public BoundaryExtractorRunner(ExportOptions options) {
        this.options = options;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        applyCommandLine(args);

        if (options.getInput() == null || options.getInput().isBlank()) {
            printUsage();
            return;
        }

        long start = System.currentTimeMillis();
        new ExtractionEngine(options).run();
        System.err.println(String.format("[info] elapsed: %.1f s",
                (System.currentTimeMillis() - start) / 1000.0));
    }

    /**
     * Accepts both positional {@code input.osm.pbf} arguments and friendly
     * {@code --key=value} flags, mirroring them into ExportOptions.
     */
    private void applyCommandLine(ApplicationArguments args) {
        for (String key : KNOWN_KEYS) {
            List<String> values = args.getOptionValues(key);
            if (values != null && !values.isEmpty()) {
                apply(key, values.get(values.size() - 1));
            }
        }
        List<String> positional = args.getNonOptionArgs();
        if (!positional.isEmpty() && (options.getInput() == null || options.getInput().isBlank())) {
            options.setInput(positional.get(0));
        }
    }

    private void apply(String key, String value) {
        switch (key) {
            case "input" -> options.setInput(value);
            case "output" -> options.setOutput(value);
            case "admin-levels", "adminLevels" -> options.setAdminLevels(value);
            case "boundary-types", "boundaryTypes" -> options.setBoundaryTypes(value);
            case "bbox" -> options.setBbox(value);
            case "simplify" -> options.setSimplify(Double.parseDouble(value));
            case "include-tags", "includeTags" -> options.setIncludeTags(Boolean.parseBoolean(value));
            case "hierarchy" -> options.setHierarchy(Boolean.parseBoolean(value));
            case "srid" -> options.setSrid(Integer.parseInt(value));
            case "precision" -> options.setPrecision(Integer.parseInt(value));
            case "verbose" -> options.setVerbose(Boolean.parseBoolean(value));
            default -> { }
        }
    }

    private void printUsage() {
        System.err.println("""
                Admin Division Extractor - extracts administrative boundaries from OSM PBF to GeoJSON
                (output format compatible with https://osm-boundaries.com/ downloads)

                Usage:
                  java -jar admin-division-extractor.jar <input.osm.pbf> [options]

                Options (osm-boundaries.com export settings in parentheses):
                  --output=<file.geojson>         output file (default: input name + .geojson)
                  --admin-levels=<2-10,7>         admin level filter (Admin level filter)
                  --boundary-types=<a,b>          boundary=* filter, e.g. administrative,national_park
                                                  (Boundary filter)
                  --bbox=<minLon,minLat,maxLon,maxLat>  geographic filter (Selection)
                  --simplify=<factor>             Visvalingam-Whyatt factor in degrees:
                                                  0.001~100m, 0.01~1km, 0.1~10km (Simplify)
                  --include-tags=<true|false>     emit all_tags (Tags)
                  --hierarchy=<true|false>        emit parents chain (Hierarchy data)
                  --srid=<4326>                   only EPSG:4326 supported (SRID)
                  --precision=<7>                 decimal digits of output coordinates
                  --verbose=<true|false>          progress / warnings

                Examples:
                  java -jar admin-division-extractor.jar china-latest.osm.pbf --admin-levels=4,6,8
                  java -jar admin-division-extractor.jar china-latest.osm.pbf \\
                       --boundary-types=administrative --admin-levels=4 --simplify=0.001
                """);
    }
}
