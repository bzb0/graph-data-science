/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.cypher;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.NodeMapping;

public class CypherGraphStore extends GraphStoreAdapter implements NodeLabelUpdater {

    CypherNodeMapping cypherNodeMapping;

    public CypherGraphStore(GraphStore graphStore) {
        super(graphStore);
        this.cypherNodeMapping = new CypherNodeMapping(super.nodes());
    }

    @Override
    public NodeMapping nodes() {
        return this.cypherNodeMapping;
    }

    @Override
    public void addNodeLabel(NodeLabel nodeLabel) {
        this.cypherNodeMapping.addNodeLabel(nodeLabel);
    }

    @Override
    public void addLabelToNode(long nodeId, NodeLabel nodeLabel) {
        this.cypherNodeMapping.addLabelToNode(nodeId, nodeLabel);
    }
}
