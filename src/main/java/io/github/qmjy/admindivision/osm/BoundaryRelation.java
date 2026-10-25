package io.github.qmjy.admindivision.osm;

import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;

import java.util.Map;

/**
 * In-memory picture of a boundary relation (type=boundary / boundary=*).
 */
public final class BoundaryRelation {

    public final long id;
    public final String boundary;
    public final String adminLevel;
    public final Map<String, String> tags;

    public final it.unimi.dsi.fastutil.longs.LongOpenHashSet outerWayIds = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    public final it.unimi.dsi.fastutil.longs.LongOpenHashSet innerWayIds = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    public BoundaryRelation(long id, String boundary, String adminLevel, Map<String, String> tags) {
        this.id = id;
        this.boundary = boundary;
        this.adminLevel = adminLevel;
        this.tags = tags;
    }

    public void addMember(RelationMember member) {
        if (member.getMemberType() == EntityType.Way) {
            String role = member.getMemberRole();
            if ("outer".equals(role)) {
                outerWayIds.add(member.getMemberId());
            } else if ("inner".equals(role)) {
                innerWayIds.add(member.getMemberId());
            }
        }
    }

    public boolean hasPolygonMembers() {
        return !outerWayIds.isEmpty();
    }
}
