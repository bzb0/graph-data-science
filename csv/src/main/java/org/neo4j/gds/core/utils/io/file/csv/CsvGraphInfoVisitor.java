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
package org.neo4j.gds.core.utils.io.file.csv;

import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.neo4j.gds.core.utils.io.file.GraphInfo;
import org.neo4j.gds.core.utils.io.file.SingleRowVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class CsvGraphInfoVisitor implements SingleRowVisitor<GraphInfo> {

    public static final String GRAPH_INFO_FILE_NAME = "graph_info.csv";
    static final String DATABASE_ID_COLUMN_NAME = "databaseId";
    static final String DATABASE_NAME_COLUMN_NAME = "databaseName";
    static final String NODE_COUNT_COLUMN_NAME = "nodeCount";
    static final String MAX_ORIGINAL_ID_COLUMN_NAME = "maxOriginalId";
    static final String BIT_ID_MAP_COLUMN_NAME = "bitIdMap";

    private final CsvAppender csvAppender;

    public CsvGraphInfoVisitor(Path fileLocation) {
        try {
            this.csvAppender = new CsvWriter().append(fileLocation.resolve(GRAPH_INFO_FILE_NAME), StandardCharsets.UTF_8);
            writeHeader();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void export(GraphInfo graphInfo) {
        try {
            this.csvAppender.appendField(graphInfo.namedDatabaseId().databaseId().uuid().toString());
            this.csvAppender.appendField(graphInfo.namedDatabaseId().name());
            this.csvAppender.appendField(Long.toString(graphInfo.nodeCount()));
            this.csvAppender.appendField(Long.toString(graphInfo.maxOriginalId()));
            this.csvAppender.appendField(Boolean.toString(graphInfo.bitIdMap()));
            this.csvAppender.endLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            this.csvAppender.flush();
            this.csvAppender.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeHeader() throws IOException {
        this.csvAppender.appendField(DATABASE_ID_COLUMN_NAME);
        this.csvAppender.appendField(DATABASE_NAME_COLUMN_NAME);
        this.csvAppender.appendField(NODE_COUNT_COLUMN_NAME);
        this.csvAppender.appendField(MAX_ORIGINAL_ID_COLUMN_NAME);
        this.csvAppender.appendField(BIT_ID_MAP_COLUMN_NAME);
        this.csvAppender.endLine();
    }
}
