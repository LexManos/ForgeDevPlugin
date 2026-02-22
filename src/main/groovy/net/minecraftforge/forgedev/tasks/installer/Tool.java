/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.util.List;

public class Tool implements Serializable {
    private final String jar;
    private final List<MinimalResolvedArtifact> classpath;

    public Tool(String jar, List<MinimalResolvedArtifact> classpath) {
        this.jar = jar;
        this.classpath = classpath;
    }

    public String getJar() {
        return this.jar;
    }

    @ApiStatus.Internal
    List<MinimalResolvedArtifact> getArtifacts() {
        return this.classpath;
    }

    public List<String> getClasspath() {
        return classpath.stream()
            .map(art -> art.info().name())
            .filter(name -> !name.equals(this.jar))
            .toList();
    }
}
