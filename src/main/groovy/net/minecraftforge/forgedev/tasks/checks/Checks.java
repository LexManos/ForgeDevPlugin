/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import net.minecraftforge.forgedev.LegacyMCPExtension;
import net.minecraftforge.forgedev.LegacyPatcherExtension;
import net.minecraftforge.forgedev.legacy.tasks.Util;
import net.minecraftforge.forgedev.tasks.installertools.ExtractInheritance;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public abstract class Checks {
    private final TaskContainer tasks;
    private final LegacyMCPExtension mcp;
    private final LegacyPatcherExtension patcher;
    private final TaskProvider<Task> checkAndFix;

    @Inject public abstract ObjectFactory getObjects();

    @Inject
    public Checks(Project project) {
        this.tasks = project.getTasks();
        this.mcp = project.getExtensions().getByType(LegacyMCPExtension.class);
        this.patcher = project.getExtensions().getByType(LegacyPatcherExtension.class);
        this.checkAndFix = tasks.register("checkAndFix", task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        });
    }

    public TaskProvider<Task> getFix() {
        return this.checkAndFix;
    }
    public void fix(Action<? super Task> action) {
        this.checkAndFix.configure(action);
    }

    private TaskProvider<ExtractInheritance> extractInheritance;
    private TaskProvider<ExtractInheritance> getExtractInheritance() {
        if (this.extractInheritance == null) {
            this.extractInheritance = tasks.register("extractInheritance", ExtractInheritance.class, task -> {
                task.getAdditionalArgs().add("--annotations");

                // TODO: [ForgeDev] Support mapped AT/SAS files
                task.getInput().set(mcp.getFiles().getJoinedSearge());
                task.getLibraries().from(mcp.getFiles().getLibraryList().map(libraries -> {
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

        }
        return this.extractInheritance;
    }

    public <T extends CheckTask> Check<T> check(String taskName, Class<T> clazz) {
        return check(taskName, clazz, Util.noop());
    }
    public <T extends CheckTask> Check<T> check(String taskName, Class<T> clazz, Action<? super T> action) {
        taskName = StringGroovyMethods.capitalize(taskName);

        var check = tasks.register("check" + taskName, clazz, task -> {
            action.execute(task);
            task.getFix().set(false);
        });
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(check));

        var fix = tasks.register("checkAndFix" + taskName, clazz, task -> {
            action.execute(task);
            task.getFix().set(true);
        });

        this.checkAndFix.configure(task -> task.dependsOn(fix));

        @SuppressWarnings("unchecked")
        var ret = (Check<T>)this.getObjects().newInstance(Check.class, check, fix);
        return ret;
    }

    private Check<CheckATs> ats;
    public Check<CheckATs> getAts() {
        if (this.ats == null) {
            var inheritance = getExtractInheritance().flatMap(ExtractInheritance::getOutput);
            this.ats = check("Ats", CheckATs.class, task -> {
                task.getAts().from(this.patcher.getAccessTransformers());
                task.getInheritance().set(inheritance);
            });
        }
        return ats;
    }
    public Check<CheckATs> ats() { return ats(Util.noop()); }
    public Check<CheckATs> ats(Action<CheckATs> action) {
        var ret = getAts();
        ret.check(action);
        ret.fix(action);
        return ret;
    }

    private Check<CheckExecs> execs;
    public Check<CheckExecs> getExecs() {
        if (this.execs == null) {
            this.execs = check("Execs", CheckExecs.class, task -> {
                task.getExcs().from(patcher.getExcs());
                task.getBinary().set(tasks.named(Jar.TASK_NAME, Jar.class).flatMap(Jar::getArchiveFile));
            });
        }
        return execs;
    }
    public Check<CheckExecs> execs() { return execs(Util.noop()); }
    public Check<CheckExecs> execs(Action<CheckExecs> action) {
        var ret = getExecs();
        ret.check(action);
        ret.fix(action);
        return ret;
    }

    private Check<CheckSAS> sas;
    public Check<CheckSAS> getSas() {
        if (this.sas == null) {
            var inheritance = getExtractInheritance().flatMap(ExtractInheritance::getOutput);
            this.sas = check("Sas", CheckSAS.class, task -> {
                task.getInheritance().set(inheritance);
                task.getSass().from(patcher.getSideAnnotationStrippers());
            });
        }
        return sas;
    }
    public Check<CheckSAS> sas(Action<CheckSAS> action) {
        var ret = getSas();
        ret.check(action);
        ret.fix(action);
        return ret;
    }

    private Check<CheckPatches> patches;
    public Check<CheckPatches> getPatches() {
        if (this.patches == null)
            this.patches = check("Patches", CheckPatches.class);
        return patches;
    }
    public Check<CheckPatches> patches() { return patches(Util.noop()); }
    public Check<CheckPatches> patches(Action<CheckPatches> action) {
        var ret = getPatches();
        ret.check(action);
        ret.fix(action);
        return ret;
    }
}
