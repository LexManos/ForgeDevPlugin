/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy;

import net.minecraftforge.forgedev.tasks.filtering.LegacyFilterNewJar;
import net.minecraftforge.forgedev.tasks.generation.GeneratePatcherConfigV2;
import net.minecraftforge.forgedev.tasks.installertools.DownloadMappings;
import net.minecraftforge.forgedev.tasks.installertools.ExtractInheritance;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerMCPSetup;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerRawArtifact;
import net.minecraftforge.forgedev.tasks.obfuscation.LegacyRenameJar;
import net.minecraftforge.forgedev.tasks.obfuscation.LegacyReobfuscateJar;
import net.minecraftforge.forgedev.tasks.patching.binary.ApplyBinPatches;
import net.minecraftforge.forgedev.tasks.patching.binary.CreateBinPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

abstract class ForgeBuildPlugin extends EnhancedPlugin<Project> {
    static final String NAME = "forge-build";
    static final String DISPLAY_NAME = "Forge Build";

    private final ForgeBuildProblems problems = getObjects().newInstance(ForgeBuildProblems.class);

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ProjectLayout getLayout();

    protected abstract @Inject ArchiveOperations getArchiveOperations();

    @Inject
    public ForgeBuildPlugin() {
        super(NAME, DISPLAY_NAME, "forgeTools");
    }

    @SuppressWarnings("removal")
    @Override
    public void setup(Project project) {
        project.getPluginManager().apply("de.undercouch.download");

        var providers = getProviders();
        var layout = getLayout();
        var archiveOperations = getArchiveOperations();

        var tasks = project.getTasks();

        project.getPluginManager().withPlugin("net.minecraftforge.forgedev", forgedevAppliedPlugin -> {
            var setupMCP = tasks.named("setupMCP", MavenizerMCPSetup.class);
            var downloadClientMappings = tasks.named("downloadClientMappings", DownloadMappings.class);
            var downloadServerMappings = tasks.named("downloadServerMappings", DownloadMappings.class);
            var jar = tasks.named("jar", Jar.class);

            var extractInheritance = tasks.register("extractInheritance", ExtractInheritance.class, task -> {
                task.setGroup("Forge downloads");
                task.dependsOn(setupMCP);

                task.getAdditionalArgs().add("--annotations");
                task.getInput().fileProvider(tasks.named("genJoinedBinPatches", CreateBinPatches.class).map(t -> t.getClean().getSingleFile()));
                task.getLibraries().from(setupMCP.flatMap(MavenizerMCPSetup::getLibrariesList).map(libraries -> {
                    try {
                        return Files.readAllLines(libraries.getAsFile().toPath()).stream()
                                .map(line -> line.substring(3)) // remove -e=
                                .map(File::new)
                                .toList();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
            });

            var createClientOfficial = tasks.register("createClientOfficial", LegacyRenameJar.class, task -> {
                task.dependsOn(setupMCP);

                task.getAdditionalArgs().addAll("--ann-fix", "--ids-fix", "--src-fix", "--record-fix", "--strip-sigs", "--reverse");
                task.getMappings().set(downloadClientMappings.flatMap(DownloadMappings::getOutput));
                task.getInput().set(setupMCP.flatMap(MavenizerMCPSetup::getClientRaw));
                task.getOutput().set(task.getDefaultOutputFile());
            });

            var createServerOfficial = tasks.register("createServerOfficial", LegacyRenameJar.class, task -> {
                task.dependsOn(setupMCP);

                task.getAdditionalArgs().addAll("--ann-fix", "--ids-fix", "--src-fix", "--record-fix", "--strip-sigs", "--reverse");
                task.getMappings().set(downloadServerMappings.flatMap(DownloadMappings::getOutput));
                task.getInput().set(setupMCP.flatMap(MavenizerMCPSetup::getServerExtracted));
                task.getOutput().set(task.getDefaultOutputFile());
            });

            var genClientBinPatches = tasks.named("genClientBinPatches", CreateBinPatches.class, task -> {
                task.getClean().setFrom(createClientOfficial.flatMap(LegacyRenameJar::getOutput));
                task.getCreate().setFrom(jar.flatMap(Jar::getArchiveFile));
            });

            var genServerBinPatches = tasks.named("genServerBinPatches", CreateBinPatches.class, task -> {
                task.getClean().setFrom(createServerOfficial.flatMap(LegacyRenameJar::getOutput));
                task.getCreate().setFrom(jar.flatMap(Jar::getArchiveFile));
            });

            var genJoinedBinPatches = tasks.named("genJoinedBinPatches", CreateBinPatches.class, task -> {
                task.getClean().setFrom(tasks.named("rawJoinedJarSrg", MavenizerRawArtifact.class).map(t -> t.getOutputs().getFiles()));
            });

            var applyClientBinPatches = tasks.register("applyClientBinPatches", ApplyBinPatches.class, task -> {
                task.getClean().setFrom(createClientOfficial.flatMap(LegacyRenameJar::getOutput));
                task.getApply().setFrom(genClientBinPatches.flatMap(CreateBinPatches::getOutput));
                task.getData().set(true);
                task.getUnpatched().set(true);
            });

            var applyServerBinPatches = tasks.register("applyServerBinPatches", ApplyBinPatches.class, task -> {
                task.getClean().setFrom(createServerOfficial.flatMap(LegacyRenameJar::getOutput));
                task.getApply().setFrom(genServerBinPatches.flatMap(CreateBinPatches::getOutput));
                task.getData().set(true);
                task.getUnpatched().set(true);
            });

            var applyJoinedBinPatches = tasks.register("applyJoinedBinPatches", ApplyBinPatches.class, task -> {
                task.getClean().setFrom(genJoinedBinPatches.map(CreateBinPatches::getClean));
                task.getApply().setFrom(genJoinedBinPatches.flatMap(CreateBinPatches::getOutput));
            });

            var applyPatches = tasks.named("applyPatches", ApplyPatches.class, task -> {
                task.getFailOnError().set(!problems.test("net.minecraftforge.forge.build.updating"));
                task.getRejects().convention(project.getRootProject().getLayout().getProjectDirectory().dir(providers.provider(() -> "rejects")).map(Directory::getAsFile));
                task.getArchiveRejects().unsetConvention();
            });

            var reobfJar = tasks.named("reobfJar", LegacyReobfuscateJar.class);

            var officialClassesJar = tasks.register("officialClassesJar", Zip.class, task -> {
                task.dependsOn(jar);

                task.getDestinationDirectory().set(layout.getBuildDirectory().dir("libs"));
                task.getArchiveClassifier().set("official-classes");
                task.getArchiveExtension().set("jar");

                task.from(providers.provider(() -> archiveOperations.zipTree(jar.flatMap(Jar::getArchiveFile))), copy -> copy
                    .include("**/*.class")
                    .exclude("mcp/**"));
            });

            var filterJarNew = tasks.named("filterJarNew", LegacyFilterNewJar.class, task -> {
                task.dependsOn(officialClassesJar);

                task.getInput().set(officialClassesJar.flatMap(Zip::getArchiveFile));
            });

            var filterJarNewSrg = tasks.register("filterJarNewSrg", LegacyFilterNewJar.class, task -> {
                task.dependsOn(reobfJar, filterJarNew);

                task.getInput().set(reobfJar.flatMap(LegacyReobfuscateJar::getOutput));
                task.getSrg().set(filterJarNew.flatMap(LegacyFilterNewJar::getSrg));
                task.getBlacklist().setFrom(filterJarNew.map(LegacyFilterNewJar::getBlacklist));
            });

            var universalJar = tasks.named("universalJar", Jar.class);

            var universalJarSrg = tasks.register("universalJarSrg", Jar.class, task -> {
                task.dependsOn(filterJarNewSrg, universalJar);

                var filterNewJarSrgOutput = filterJarNewSrg.flatMap(LegacyFilterNewJar::getOutput);
                var universalJarOutput = universalJar.flatMap(Jar::getArchiveFile);
                task.from(providers.provider(() -> archiveOperations.zipTree(filterNewJarSrgOutput)));
                task.from(providers.provider(() -> archiveOperations.zipTree(universalJarOutput)));
                task.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);

                task.getArchiveClassifier().set("universal-srg");
                task.setManifest(universalJar.map(Jar::getManifest).get());
            });

            var userdevConfig = tasks.named("userdevConfig", GeneratePatcherConfigV2.class, task -> {
                task.getUniversal().set("%s:%s:%s:universal-srg@jar".formatted(project.getGroup(), project.getName(), project.getVersion()));
            });
        });
    }
}
