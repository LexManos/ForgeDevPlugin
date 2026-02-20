/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.Zip;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;

public abstract class InstallerJar extends Zip {
    @Inject
    public InstallerJar() {
        // We have to `set` here because the default plugin forces the conventions after the task is created
        // But since configuration is done in order, callers can override in their actions
        this.getArchiveClassifier().set("installer");
        this.getArchiveExtension().set("jar"); // We use Zip task so it doesn't overwrite the Manifest
        // Technically this should pull from BasePluginExtension, but there is a to-do in gradle to break that so I don't care.
        // https://github.com/gradle/gradle/blob/fb1720293a5c90a642f636843e6793555761e64e/platforms/jvm/plugins-java-base/src/main/java/org/gradle/api/plugins/JavaBasePlugin.java#L354
        this.getDestinationDirectory().set(getProject().getLayout().getBuildDirectory().dir("libs"));
    }

    @ApiStatus.Internal
    public void pack(Provider<MinimalResolvedArtifact> info) {
        this.from(info.map(MinimalResolvedArtifact::file), spec -> {
            spec.rename( name -> {
                var path = info.get().info().path();
                getProject().getLogger().lifecycle("Adding: " + path);
                return "maven/" + path;
            });
            spec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });
    }
}
