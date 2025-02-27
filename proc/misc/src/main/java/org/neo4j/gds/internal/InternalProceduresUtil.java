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
package org.neo4j.gds.internal;

import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.kernel.api.procedure.GlobalProcedures;

final class InternalProceduresUtil {

    static <T> T resolve(Context ctx, Class<T> dependency) {
        return GraphDatabaseApiProxy.resolveDependency(ctx.dependencyResolver(), dependency);
    }

    static <T> T lookup(Context ctx, Class<T> component) throws ProcedureException {
        return lookup(ctx, resolve(ctx, GlobalProcedures.class), component);
    }

    static <T> T lookup(Context ctx, GlobalProcedures procedures, Class<T> component) throws ProcedureException {
        return Neo4jProxy.lookupComponentProvider(procedures, component, false).apply(ctx);
    }

    private InternalProceduresUtil() {}
}
