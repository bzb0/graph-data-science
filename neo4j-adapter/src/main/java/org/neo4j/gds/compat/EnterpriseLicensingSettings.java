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
package org.neo4j.gds.compat;

import org.neo4j.annotations.service.ServiceProvider;
import org.neo4j.configuration.Description;
import org.neo4j.configuration.SettingValueParsers;
import org.neo4j.configuration.SettingsDeclaration;
import org.neo4j.graphdb.config.Setting;

import static org.neo4j.configuration.SettingImpl.newBuilder;

@ServiceProvider
public class EnterpriseLicensingSettings implements SettingsDeclaration {

    private static final String ENTERPRISE_LICENSE_FILE = "gds.enterprise.license_file";

    @Description("Sets the location of the file that contains the key to unlock the Enterprise Edition of the Neo4j Graph Data Science library")
    public static final Setting<String> enterpriseLicenseFile = newBuilder(
        ENTERPRISE_LICENSE_FILE,
        SettingValueParsers.STRING,
        null
    ).build();
}
