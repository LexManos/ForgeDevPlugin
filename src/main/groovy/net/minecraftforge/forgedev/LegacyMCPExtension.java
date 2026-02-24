/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.forgedev.legacy.values.MavenInfo;
import net.minecraftforge.forgedev.tasks.mcp.MCPSetupFiles;
import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import net.minecraftforge.util.data.json.JsonData;
import org.gradle.api.Transformer;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLauncher;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class LegacyMCPExtension {
    public static final String EXTENSION_NAME = "mcp";

    private final Property<String> config = this.getObjects().property(String.class);
    private final Property<String> version = this.getObjects().property(String.class).value(
        config.map(s -> MavenInfo.from(s).art().version())
    );
    private final Map<String, MinecraftFiles> files = new HashMap<>();
    private final ForgeDevPlugin plugin;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    private final Problems problems = getObjects().newInstance(Problems.class);
    public static abstract class Problems extends EnhancedProblems {
        @Inject
        public Problems() {
            super(ForgeDevPlugin.NAME + '.' + EXTENSION_NAME, ForgeDevPlugin.DISPLAY_NAME + " - MCP");
        }
    }

    @Inject
    public LegacyMCPExtension(ForgeDevPlugin plugin) {
        this.plugin = plugin;
    }

    public Property<String> getConfig() {
        return this.config;
    }

    public Property<String> getVersion() {
        return this.version;
    }

    public void setConfig(Provider<String> value) {
        getConfig().set(value.map(s -> {
            if (s.indexOf(':') != -1) { // Full artifact
                return s;
            } else {
                return "de.oceanlabs.mcp:mcp_config:" + s + "@zip";
            }
        }));
    }

    public void setConfig(String value) {
        setConfig(this.getProviders().provider(() -> value));
    }

    public abstract Property<String> getPipeline();

    public String getArtifact() {
        return this.getConfig().get();
    }

    public Provider<String> getArtifact(String classifier) {
        return getArtifact(classifier, "jar");
    }
    public Provider<String> getArtifact(String classifier, String extension) {
        return this.getConfig().map(str -> {
            var info = MavenInfo.from(str).art();
            var _new = MavenInfo.from(info.group(), info.name(), info.version(), classifier, extension);
            return _new.name();
        });
    }

    public MinecraftFiles getFiles() {
        return getFiles(getVersion().get());
    }
    public MinecraftFiles getFiles(String version) {
        var ret = this.files.get(version);
        if (ret == null) {
            //var client = mavenizer("client", version, false);
            //var server = mavenizer("server", version, false);
            var joined = mavenizer("joined", version, false);

            ret = getObjects().newInstance(MinecraftFiles.class, plugin, joined);
            this.files.put(version, ret);
        }
        return ret;
    }

    private Provider<MCPSetupFiles> mavenizer(String pipeline, String version, boolean searge) {
        var fileName = "mavenizer/mcp-" + version + "-files-" + pipeline;
        if (searge)
            fileName += "-searge";
        // TODO: [ForgeDevPlugin] Make single mavenizer task to get the 'vanilla' files we need
        // We also need a way to get the 'slim' artifacts for older versions which don't use bundled server jar
        // Could still name it 'serverExtracted' in the json
        var output = this.plugin.localCaches().file("mavenizer/" + fileName + ".jar").get().getAsFile();
        var outputJson = this.plugin.localCaches().file("mavenizer/" + fileName + ".json").get().getAsFile();

        return this.getProviders().of(MavenizerValueSource.class, spec -> {
            spec.parameters(params -> {
                var tool = this.plugin.getTool(Tools.MAVENIZER);
                params.getClasspath().setFrom(tool.getClasspath());
                params.getJavaLauncher().set(tool.getJavaLauncher().map(JavaLauncher::getExecutablePath));
                params.getArguments().set(this.getProviders().provider(() -> {
                    var toolCache = this.plugin.globalCaches()
                            .dir(tool.getName().toLowerCase(Locale.ENGLISH))
                            .map(this.problems.ensureFileLocation());
                    var cache = toolCache.get().dir("caches").getAsFile().getAbsolutePath();

                    var ret = new ArrayList<>(List.of(
                            "--mcp",
                            "--cache", cache,
                            "--jdk-cache", cache,
                            "--version", version,
                            "--raw",
                            "--pipeline", pipeline,
                            "--output", output.getAbsolutePath(),
                            "--output-files", outputJson.getAbsolutePath()
                    ));
                    if (searge)
                        ret.add("--searge");
                    return ret;
                }));
            });
        })
        .map(v -> JsonData.fromJson(outputJson, MCPSetupFiles.class));
    }

    public static abstract class MinecraftFiles {
        private final ForgeDevPlugin plugin;
        private final Provider<MCPSetupFiles> info;
        protected abstract @Inject ObjectFactory getObjects();
        protected abstract @Inject ProviderFactory getProviders();

        @Inject
        public MinecraftFiles(ForgeDevPlugin plugin, Provider<MCPSetupFiles> info) {
            this.plugin = plugin;
            this.info = info;
        }

        private Provider<RegularFile> get(Transformer<String, MCPSetupFiles> field) {
            return this.info.map(field).flatMap(this.plugin.rootProjectDirectory()::file);
        }
        public Provider<RegularFile> getLauncherManifest() {
            return get(info -> info.versionManifest);
        }
        public Provider<RegularFile> getVersionJson() {
            return get(info -> info.versionJson);
        }
        public Provider<RegularFile> getClient() {
            return get(info -> info.clientRaw);
        }
        public Provider<RegularFile> getClientMappings() {
            return get(info -> info.clientMappings);
        }
        public Provider<RegularFile> getServer() {
            return get(info -> info.serverRaw);
        }
        public Provider<RegularFile> getServerExtracted() {
            return get(info -> info.serverExtracted);
        }
        public Provider<RegularFile> getServerMappings() {
            return get(info -> info.serverMappings);
        }
    }
}
