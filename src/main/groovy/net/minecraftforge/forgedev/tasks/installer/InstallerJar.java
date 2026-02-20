/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import net.minecraftforge.util.download.DownloadUtils;
import org.gradle.api.Action;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.Zip;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public abstract class InstallerJar extends Zip {
    private final Provider<Installer> installer;

    @Inject
    public InstallerJar(Provider<Installer> installer) {
        this.installer = installer;
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

    @ApiStatus.Internal
    public void libraries(Provider<List<MinimalResolvedArtifact>> libraries) {
        // TODO: [ForgeDev][LazyConfig] See if we can trick CopySpec into allowing an empty list
        for (var artifact : libraries.get()) {
            library(this.getProject().provider(() -> artifact), lib -> {});
        }
    }

    @ApiStatus.Internal
    public void library(Provider<MinimalResolvedArtifact> provider, Action<LibraryInfo> action) {
        // TODO: [ForgeDev][LazyConfig] See if we can trick CopySpec into allowing an empty list
        var info = provider.map(LibraryInfo::from).map(LibraryInfo.apply(action)).get();
        var artifact = info.downloads().artifact();
        var offline = installer.get().getOffline().get();

        // If we are not making an offline installer, and we're on the CI don't check remote, assume we're gunna publish everything
        if (!offline && installer.get().getCi().get()) {
            getProject().getLogger().lifecycle("Skipping: " + artifact.path);
            return;
        }

        // If it's an offline jar, always pack
        var pack = offline || artifact.url.isEmpty();

        // If it's not, Check if the remote
        if (!pack) {
            try {
                // See if the remote hash is the same as ours
                var remote = DownloadUtils.downloadString(artifact.url + ".sha1");
                pack = !artifact.sha1.equals(remote);
            } catch (FileNotFoundException e) {
                // The file doesn't exist, Mojang's maven doesn't include them, so assume it exists if it's on there.
                pack = !artifact.url.startsWith("https://libraries.minecraft.net/");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (pack)
            pack(provider);
        else
            getProject().getLogger().lifecycle("Skipping: " + artifact.path);
    }
}
