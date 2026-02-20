/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer;

import groovy.json.JsonBuilder;
import net.minecraftforge.forgedev.legacy.tasks.Util;
import net.minecraftforge.forgedev.legacy.values.LibraryInfo;
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class LauncherJson extends DefaultTask {
    protected abstract @Inject ProviderFactory getProviders();
    protected abstract @Inject ObjectFactory getObjects();

    @OutputFile abstract RegularFileProperty getOutput();

    @InputFiles abstract ConfigurableFileCollection getInput();
    @Input abstract Property<String> getTimestamp();
    @Input abstract Property<String> getReleaseTime();
    @Input abstract Property<String> getId();
    @Input @Optional abstract Property<String> getInheritsFrom();
    @Input abstract Property<String> getType();
    @Input @Optional abstract Property<String> getMainClass();
    @Input @Optional abstract ListProperty<Object> getGameArgs();
    @Input @Optional abstract ListProperty<Object> getJvmArgs();
    @Input abstract ListProperty<LibraryInfo> getLibraries();

    @Inject
    public LauncherJson() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().file("libs/version.json"));

        // TODO: [ForgeDev][Reproduceable] Add helper to get timestamp from latest git commit
        var timestamp = Util.iso8601Now();
        getTimestamp().convention(timestamp);
        getReleaseTime().convention(timestamp);
        getType().convention("release");
        getMainClass().convention("main");

    }

    @ApiStatus.Internal
    public void libraries(Configuration config) {
        this.getInput().from(config);
        this.getLibraries().addAll(MinimalResolvedArtifact.from(getProject(), config).map(LibraryInfo::toList));
    }
    @ApiStatus.Internal
    public void library(Provider<MinimalResolvedArtifact> info, Action<LibraryInfo> action) {
        this.getInput().from(info.map(MinimalResolvedArtifact::file));
        this.getLibraries().add(info.map(LibraryInfo::from).map(LibraryInfo.apply(action)));
    }

    @TaskAction
    protected void exec() throws IOException {
        var json = new LinkedHashMap<String, Object>();
        json.put("_comment", Installer.JSON_COMMENT);
        json.put("id", getId().get());
        json.put("time", getTimestamp().get());
        json.put("releaseTime", getReleaseTime().get());
        if (getInheritsFrom().isPresent())
            json.put("inheritsFrom", getInheritsFrom().get());
        json.put("type", getType().get());
        json.put("logging", Map.of()); // Hardcoded for now, if we ever wanna move to remotely hosted log configs we could
        json.put("mainClass", getMainClass().orElse(""));

        // I could model out this and add support for rules and shit.. but I dont want to
        var args = new LinkedHashMap<String, List<Object>>();
        if (getGameArgs().isPresent())
            args.put("game",  getGameArgs().get());
        if (getJvmArgs().isPresent())
            args.put("jvm",  getJvmArgs().get());
        if (!args.isEmpty())
            json.put("arguments", args);

        var libraries = new ArrayList<>(this.getLibraries().get());
        var seen = new HashSet<String>();
        //libraries.sort(Comparator.comparing(LibraryInfo::name));
        libraries.removeIf(l -> !seen.add(l.name()));
        json.put("libraries", libraries);

        getLogger().lifecycle("Launcher Json " + json.toString());
        var output = getOutput().get().getAsFile().toPath();
        getLogger().lifecycle("Launcher File " + output.toString());
        var jsonData = new JsonBuilder(json).toPrettyString();
        getLogger().lifecycle("Launcher Written");
        Files.writeString(output, jsonData);
    }
}
