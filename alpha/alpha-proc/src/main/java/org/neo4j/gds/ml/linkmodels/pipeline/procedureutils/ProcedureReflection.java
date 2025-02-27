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
package org.neo4j.gds.ml.linkmodels.pipeline.procedureutils;

import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.BaseProc;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.procedure.Procedure;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

/**
 * Implements finding, filtering and executing AlgoBaseProc's for the purpose of Pipelines.
 * Error messages are therefore specific to this context.
 */
public final class ProcedureReflection {

    private static final List<String> PACKAGES_TO_SCAN = List.of(
        "org.neo4j.gds"
    );

    private final List<Method> procedureMethods;
    public static final ProcedureReflection INSTANCE = new ProcedureReflection();

    private ProcedureReflection() {
        procedureMethods = PACKAGES_TO_SCAN.stream()
            .map(this::createReflections)
            .map(r -> r.getMethodsAnnotatedWith(Procedure.class))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    public Method findProcedureMethod(String procName) {
        List<Method> foundMethods = filterAlgoBaseMethods(procName);
        if (foundMethods.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale("Invalid procedure name `%s` for pipelining.", procName));
        }
        if (foundMethods.size() > 1) {
            throw new IllegalArgumentException(formatWithLocale(
                "Ambiguous procedure name `%s`. Found matching procedures %s.",
                procName,
                foundMethods.stream()
                    .map(this::procedureName)
                    .collect(Collectors.joining(", "))
            ));
        }
        return foundMethods.get(0);
    }

    private List<Method> filterAlgoBaseMethods(String shortName) {
        return procedureMethods
                .stream()
                .filter(method -> {
                    if (!AlgoBaseProc.class.isAssignableFrom(method.getDeclaringClass())) {
                        return false;
                    }

                    return validShortName(procedureName(method), shortName);
                })
                .collect(Collectors.toList());
    }

    private static boolean validShortName(String fullName, String shortName) {
        // '.' as for example `.ageRank` is not valid short for `gds.pageRank`, but `.pageRank` is.
        var normalizedFullName = "." + fullName;
        var normalizedShortName = "." + shortName + (shortName.endsWith(".mutate") ? "" : ".mutate");
        return normalizedFullName.endsWith(normalizedShortName);
    }

    public String procedureName(Method method) {
        var annotation = method.getAnnotation(Procedure.class);
        return annotation.name().isEmpty() ? annotation.value() : annotation.name();
    }

    private AlgoBaseProc<?, ?, ?> createProcedure(BaseProc caller, Method method) {
        AlgoBaseProc<?, ?, ?> proc;
        try {
            proc = (AlgoBaseProc<?, ?, ?>) method.getDeclaringClass().getConstructor().newInstance();
            proc.api = caller.api;
            proc.callContext = caller.callContext;
            proc.log = caller.log;
            proc.procedureTransaction = caller.procedureTransaction;
            proc.allocationTracker = caller.allocationTracker;
            proc.transaction = caller.transaction;
            proc.taskRegistry = caller.taskRegistry;
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return proc;
    }

    public Optional<AlgoBaseConfig> createAlgoConfig(BaseProc caller, Method procMethod, CypherMapWrapper config) {
        try {
            var proc = createProcedure(caller, procMethod);
            var newConfigMethod = proc.getClass().getDeclaredMethod("newConfig", String.class, Optional.class, Optional.class, CypherMapWrapper.class);
            // make protected `newConfig` method accessible
            newConfigMethod.setAccessible(true);
            // validate mandatory algo specific fields are given
            return Optional.of((AlgoBaseConfig) newConfigMethod.invoke(proc, "", Optional.empty(), Optional.empty(), config));
        } catch (InvocationTargetException e) {
            // propagate IllegalArgument exception
            throw new IllegalArgumentException(e.getTargetException().getMessage(), e.getTargetException().getCause());
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return Optional.empty();
        }
    }

    public void invokeProc(BaseProc caller, String graphName, Method procMethod, Map<String, Object> config) {
        AlgoBaseProc<?, ?, ?> procedure = createProcedure(caller, procMethod);
        try {
            procMethod.invoke(procedure, graphName, config);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private Reflections createReflections(String pkg) {
        return new Reflections(
            pkg,
            new MethodAnnotationsScanner()
        );
    }
}
