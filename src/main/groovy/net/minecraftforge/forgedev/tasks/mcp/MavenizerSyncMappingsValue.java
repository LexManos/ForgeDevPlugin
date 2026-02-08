/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mcp;

import net.minecraftforge.gradleutils.shared.Tool;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

public abstract class MavenizerSyncMappingsValue extends MavenizerValueSource<Integer, MavenizerSyncMappingsValue.Parameters> {
    public interface Parameters extends MavenizerValueSource.Parameters {
        Property<String> getVersion();

        Property<String> getParchmentVersion();

        DirectoryProperty getOutput();
    }

    //net.minecraft:mappings_channel:version@zip
    //--maven --artifact net.minecraft:mappings --version 1.21.7
    @Override
    protected void addArguments(List<String> args) {
        super.addArguments(args);

        var parameters = getParameters();
        args.addAll(List.of(
            "--maven",
            "--output", parameters.getOutput().get().getAsFile().getAbsolutePath(),
            "--artifact", "net.minecraft:mappings",
            "--version", parameters.getVersion().get()
        ));

        if (parameters.getParchmentVersion().isPresent()) {
            args.add("--parchment");
            args.add(parameters.getParchmentVersion().get());
        }
    }

    @Override
    public Integer obtain() {
        return this.exec();
    }
}
