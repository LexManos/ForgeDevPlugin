/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mcp;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

public abstract class MavenizerMCPMavenValue extends MavenizerValueSource<Integer, MavenizerMCPMavenValue.Parameters> {
    public interface Parameters extends MavenizerValueSource.Parameters {
        Property<String> getArtifact();

        DirectoryProperty getOutput();
    }

    @Override
    protected void addArguments(List<String> args) {
        super.addArguments(args);

        var parameters = getParameters();
        args.addAll(List.of(
            "--maven", "--global-auxiliary-variants", "--dependencies-only",
            "--output", parameters.getOutput().get().getAsFile().getAbsolutePath()
        ));

        var artifact = parameters.getArtifact().get();
        if (artifact.indexOf(':') < 0) {
            args.addAll(List.of(
                "--artifact", "net.minecraft:joined",
                "--version", artifact
            ));
        } else {
            args.addAll(List.of(
                "--artifact", artifact
            ));
        }
    }

    @Override
    public Integer obtain() {
        return this.exec();
    }
}
