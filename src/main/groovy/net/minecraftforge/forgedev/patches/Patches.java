/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.base.PatcherBase;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskProvider;

import java.io.IOException;
import java.nio.file.Files;

public interface Patches {
    String DEFAULT_NAME = "patches";

    static Patches register(Project project, ForgeDevExtension extension, PatcherBase base) {
        var ret = project.getObjects().newInstance(PatchesImpl.class, extension, DEFAULT_NAME, base, project.getTasks());
        var problems = extension.getProblems();

        ret.getApply().configure(task -> {
            task.getInputFile().fileProvider(ret.getBase().getNamedSources());
            task.setPatches(ret.getPatches());
            task.getFailOnError().set(false);

            if (problems.test("net.minecraftforge.forge.build.updating")) {
                task.getMode().set("fuzzy");
                task.setRejects(project.getLayout().getProjectDirectory().dir("rejects"));
                task.getArchiveRejects().unsetConvention();
                task.getFailOnError().set(false);
            }
        });

        ret.getMake().configure(task -> {
            task.setOnlyIf(t -> ret.getPatches().isPresent());
            task.getAutoHeader().set(true);
            task.getLineEndings().convention("\n");
            task.setOutput(ret.getPatches().get());
        });

        // Is this needed? AfterEvaluate should be avoided
        project.afterEvaluate(p -> {
            // Automatically create the patches folder if it does not exist
            try {
                Files.createDirectories(ret.getPatches().get().getAsFile().toPath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create patches folder", e);
            }
        });

        return ret;
    }

    PatcherBase getBase();
    void setBase(PatcherBase base);

    String getName();
    TaskProvider<ApplyPatches> getApply();
    default void apply(Action<? super ApplyPatches> action) {
        getApply().configure(action);
    }

    TaskProvider<GeneratePatches> getMake();
    default void make(Action<? super GeneratePatches> action) {
        getMake().configure(action);
    }

    DirectoryProperty getPatches();
    DirectoryProperty getPatched();
}
