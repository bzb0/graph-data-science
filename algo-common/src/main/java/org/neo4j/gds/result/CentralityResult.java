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
package org.neo4j.gds.result;

import org.neo4j.gds.api.nodeproperties.DoubleNodeProperties;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.write.NodePropertyExporter;

public class CentralityResult {

    protected final HugeDoubleArray result;

    public CentralityResult(HugeDoubleArray result) {
        this.result = result;
    }

    public HugeDoubleArray array() {
        return this.result;
    }

    public void export(String propertyName, NodePropertyExporter exporter) {
        exporter.write(
            propertyName,
            asNodeProperties()
        );
    }

    public double score(final long nodeId) {
        return result.get(nodeId);
    }

    public DoubleNodeProperties asNodeProperties() {
        return result.asNodeProperties();
    }
}
