/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy;

import net.minecraftforge.forgedev.legacy.tasks.Util;
import net.minecraftforge.forgedev.legacy.tasks.WriteManifest;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.GenerateEclipseProject;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import javax.inject.Inject;
import java.util.List;

abstract class SharedBuildPlugin implements Plugin<Project> {
    static {
        Util.init();
    }

    protected abstract @Inject ProjectLayout getLayout();

    @Inject
    public SharedBuildPlugin() { }

    @Override
    public void apply(Project project) {
        project.setGroup("net.minecraftforge");

        var layout = getLayout();

        var tasks = project.getTasks();

        var generateResources = SharedUtil.runFirst(project, tasks.register("generateResources"));
        var processResources = tasks.named("processResources", ProcessResources.class, task ->
            task.dependsOn(generateResources)
        );

        project.getPluginManager().withPlugin("java", javaAppliedPlugin -> {
            tasks.withType(Javadoc.class).configureEach(task -> {
                task.options(minimalOptions -> {
                    if (minimalOptions instanceof CoreJavadocOptions coreOptions) {
                        coreOptions.setMemberLevel(JavadocMemberLevel.PUBLIC);
                        coreOptions.addBooleanOption("Xdoclint:all", true);
                        coreOptions.addBooleanOption("-Xdoclint:missing", true);
                    }
                });
            });

            tasks.withType(JavaCompile.class).configureEach(task -> {
                var options = task.getOptions();
                options.setWarnings(false); // Shutup deprecated for removal warnings
                options.getForkOptions().setMemoryMaximumSize("6G"); // Needed to make compiling faster, and not run out of heap space in some cases.
            });
        });

        project.getPluginManager().withPlugin("eclipse", eclipseAppliedPlugin -> {
            var eclipse = project.getExtensions().getByType(EclipseModel.class);

            eclipse.synchronizationTasks(
                processResources,
                tasks.named("eclipseClasspath", GenerateEclipseClasspath.class),
                tasks.named("eclipseProject", GenerateEclipseProject.class)
            );
        });

        project.afterEvaluate(this::finish);
    }

    private void finish(Project project) {
        var tasks = project.getTasks();

        var jar = project.getPluginManager().hasPlugin("net.minecraftforge.forgedev") ? "universalJar" : "jar";
        WriteManifest.register(project, tasks.named(jar, Jar.class));

        for (var sourceSet : project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
            var existing = tasks.getNames();
            if (!existing.contains(sourceSet.getSourcesJarTaskName())
                || !existing.contains(sourceSet.getProcessResourcesTaskName()))
                continue;

            var processResources = tasks.named(sourceSet.getProcessResourcesTaskName());
            var names = List.of(
                sourceSet.getCompileJavaTaskName(),
                sourceSet.getCompileTaskName("groovy"),
                sourceSet.getSourcesJarTaskName()
            );
            for (var name : names) {
                if (existing.contains(name))
                    tasks.named(name, task -> task.dependsOn(processResources));
            }
        }
    }
}
