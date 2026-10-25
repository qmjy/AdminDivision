package io.github.qmjy.admindivision.osm;

import crosby.binary.osmosis.OsmosisReader;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.RunnableSource;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thin wrapper around the Osmosis PBF reader. The file is streamed once per
 * {@link #scan} call; entities arrive grouped by type (nodes, then ways,
 * then relations).
 */
public final class OsmPbfScanner {

    private OsmPbfScanner() {
    }

    public static void scan(File pbfFile,
                            Consumer<Node> onNode,
                            Consumer<Way> onWay,
                            Consumer<Relation> onRelation) throws InterruptedException {
        RunnableSource reader = new OsmosisReader(pbfFile);
        reader.setSink(new Sink() {
            @Override
            public void initialize(Map<String, Object> metaData) {
            }

            @Override
            public void process(EntityContainer entityContainer) {
                if (entityContainer instanceof NodeContainer nc) {
                    onNode.accept(nc.getEntity());
                } else if (entityContainer instanceof WayContainer wc) {
                    onWay.accept(wc.getEntity());
                } else if (entityContainer instanceof RelationContainer rc) {
                    onRelation.accept(rc.getEntity());
                }
            }

            @Override
            public void complete() {
            }

            @Override
            public void close() {
            }
        });
        Thread thread = new Thread(reader, "pbf-reader");
        thread.start();
        thread.join();
    }
}
