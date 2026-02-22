/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.tasks

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

import javax.inject.Inject

@CompileStatic
abstract class WriteManifest extends DefaultTask {
    static TaskProvider<WriteManifest> register(Project project, TaskProvider<? extends Jar> jar) {
        project.tasks.register('writeManifest', WriteManifest).tap { task ->
            project.tasks.named('processResources', ProcessResources) {
                it.dependsOn(task)
            }

            project.afterEvaluate {
                try (var os = new ByteArrayOutputStream()) {
                    // RATIONALE: ManifestInternal has not changed since Gradle 2.14
                    // Due to the hacky nature of needing the proper manifest in the resources, this is the only good way of doing this
                    // The DefaultManifest object cannot be serialized into the Gradle cache, and the normal Manifest interface does not have this method
                    // This should be the only Gradle internals we need to use in all of ForgeDev, thankfully
                    (jar.get().manifest as ManifestInternal).writeTo(os)
                    task.get().inputBytes.set(os.toByteArray())
                }
            }
        }
    }

    protected abstract @Input Property<byte[]> getInputBytes()
    protected abstract @OutputFile RegularFileProperty getOutput()

    @Inject
    WriteManifest(ProjectLayout layout) {
        // The output name is ALWAYS "MANIFEST.MF", and output cannot be changed
        this.output.value(layout.projectDirectory.file("src/main/resources/META-INF/MANIFEST.MF")).disallowChanges()
    }

    @TaskAction
    void exec() {
        this.output.get().asFile.bytes = this.inputBytes.get()
    }
}
