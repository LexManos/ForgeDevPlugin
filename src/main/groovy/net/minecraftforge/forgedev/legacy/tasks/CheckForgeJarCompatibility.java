/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.tasks;

import de.undercouch.gradle.tasks.download.Download;
import net.minecraftforge.forgedev.legacy.values.LatestForgeVersion;
import net.minecraftforge.forgedev.tasks.jarcompat.CheckJarCompatibility;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerRawArtifact;
import net.minecraftforge.forgedev.tasks.obfuscation.LegacyReobfuscateJar;
import net.minecraftforge.forgedev.tasks.patching.binary.ApplyBinPatches;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class CheckForgeJarCompatibility {
    private static final String TASK_NAME = "checkJarCompatibility";

    public static TaskProvider<CheckJarCompatibility> register(Project project, String minecraftVersion, Action<? super CheckJarCompatibility> action) {
        if (project.getTasks().getNames().contains(TASK_NAME))
            throw new IllegalStateException("Cannot register " + TASK_NAME + " more than once");

        var baseForgeVersion = project.getObjects().property(String.class).value(project.getProviders().of(LatestForgeVersion.class, LatestForgeVersion.parameters(project, minecraftVersion)));
        Spec<? super Task> baseForgeVersionOnlyIf = t -> baseForgeVersion.isPresent();
        var baseForgeUserdev = project.getLayout().getBuildDirectory().file(project.provider(() -> TASK_NAME + "/forge-" + baseForgeVersion.getOrElse("null") + "-userdev.jar"));
        var baseForgeUniversal = project.getLayout().getBuildDirectory().file(project.provider(() -> TASK_NAME + "/forge-" + baseForgeVersion.getOrElse("null") + "-universal.jar"));

        var downloadBaseForgeUserdev = project.getTasks().register("downloadBaseForgeUserdev", Download.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by downloading the latest available UserDev.");
            task.onlyIf(baseForgeVersionOnlyIf);

            task.src("https://maven.minecraftforge.net/net/minecraftforge/forge/" + baseForgeVersion.getOrElse("null") + "/forge-" + baseForgeVersion.getOrElse("null") + "-userdev.jar");
            task.dest(baseForgeUserdev);
        });

        var extractBaseForgeUserdevBinPatches = project.getTasks().register("extractBaseForgeUserdevBinPatches", UserdevBinPatches.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by extracting the binary patches from the latest available UserDev.");
            task.onlyIf(baseForgeVersionOnlyIf);
            task.dependsOn(downloadBaseForgeUserdev);

            task.getBaseForgeUserdev().fileProvider(downloadBaseForgeUserdev.map(Download::getDest));
        });

        var applyBaseCompatibilityJarBinPatchesOutput = project.getLayout().getBuildDirectory().file("applyBaseCompatibilityJarBinPatches/output.jar");
        var applyBaseCompatibilityJarBinPatches = project.getTasks().register("applyBaseCompatibilityJarBinPatches", ApplyBinPatches.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by applying the base jar's binary patches from the latest available UserDev.");
            task.onlyIf(baseForgeVersionOnlyIf);
            task.dependsOn(extractBaseForgeUserdevBinPatches);

            task.getClean().setFrom(task.getProject().getTasks().named("rawJoinedJarSrg", MavenizerRawArtifact.class).flatMap(MavenizerRawArtifact::getOutput));
            task.getApply().setFrom(extractBaseForgeUserdevBinPatches.flatMap(UserdevBinPatches::getBaseBinPatchesOutput));
            task.getOutput().set(applyBaseCompatibilityJarBinPatchesOutput);
        });

        var downloadBaseForgeUniversal = project.getTasks().register("downloadBaseForgeUniversal", Download.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by downloading the latest available universal JAR.");
            task.onlyIf(baseForgeVersionOnlyIf);

            task.src("https://maven.minecraftforge.net/net/minecraftforge/forge/" + baseForgeVersion.getOrElse("null") + "/forge-" + baseForgeVersion.getOrElse("null") + "-universal.jar");
            task.dest(baseForgeUniversal);
        });

        var mergeBaseForgeJar = project.getTasks().register("mergeBaseForgeJar", MergeJars.class, task -> {
            task.setDescription("Sets up JAR compatibility checking by merging the universal JAR with the binary patched JAR.");
            task.onlyIf(baseForgeVersionOnlyIf);
            task.dependsOn(downloadBaseForgeUniversal);

            task.getInputJars().from(
                applyBaseCompatibilityJarBinPatches.flatMap(ApplyBinPatches::getOutput),
                downloadBaseForgeUniversal.map(Download::getDest)
            );
        });

        var reobfJar = project.getTasks().named("reobfJar", LegacyReobfuscateJar.class);
        var checkJarCompatibility = project.getTasks().register(TASK_NAME, CheckJarCompatibility.class, task -> {
            var rawJoinedJarSrg = task.getProject().getTasks().named("rawJoinedJarSrg", MavenizerRawArtifact.class);

            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Checks the JAR compatibility between the built JAR and the latest available JAR.");
            task.onlyIf(t -> baseForgeVersion.isPresent());
            task.dependsOn(rawJoinedJarSrg, mergeBaseForgeJar);

            task.getBaseJar().set(mergeBaseForgeJar.flatMap(MergeJars::getOutput));
            task.getBaseLibraries().from(rawJoinedJarSrg.flatMap(MavenizerRawArtifact::getOutput));

            task.getInputJar().set(reobfJar.flatMap(LegacyReobfuscateJar::getOutput));
        });
        checkJarCompatibility.configure(action);
        return checkJarCompatibility;
    }

    static abstract class UserdevBinPatches extends DefaultTask {
        protected abstract @InputFile @Optional RegularFileProperty getBaseForgeUserdev();

        abstract @OutputFile RegularFileProperty getBaseBinPatchesOutput();

        protected abstract @Inject ProjectLayout getLayout();
        protected abstract @Inject ArchiveOperations getArchiveOperations();

        @Inject
        public UserdevBinPatches() {
            getBaseBinPatchesOutput().convention(getLayout().getBuildDirectory().file(getName() + "/joined.lzma"));
        }

        @TaskAction
        protected void exec() {
            var joinedLzma = getArchiveOperations().zipTree(getBaseForgeUserdev()).matching(it -> it.include("joined.lzma")).getSingleFile();
            try {
                Files.copy(joinedLzma.toPath(), getBaseBinPatchesOutput().getAsFile().get().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
