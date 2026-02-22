/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.tasks

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider

import javax.inject.Inject

@CompileStatic
abstract class DownloadDependency extends DefaultTask {
    static TaskProvider<DownloadDependency> register(Project project, String name, Object dependency) {
        return project.tasks.register(name, DownloadDependency) {
            it.artifact = dependency;
        }
    }

    public void setArtifact(Object dependency) {
        final def unpacked
        if (dependency instanceof ProviderConvertible<?>)
            unpacked = dependency.asProvider().get()
        else if (dependency instanceof Provider<?>)
            unpacked = dependency.get()
        else
            unpacked = dependency

        var configuration = project.configurations.detachedConfiguration(
            project.dependencies.create(unpacked instanceof Dependency && !(unpacked instanceof MinimalExternalModuleDependency) ? unpacked.copy() : unpacked) { ModuleDependency it ->
                if (it instanceof ModuleDependency) {
                    it.transitive = false
                    if (it instanceof ExternalModuleDependency) {
                        it.changing = false
                    }
                }
            }
        )

        output.fileProvider(providers.provider {
            try {
                configuration.singleFile
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException('Downloaded dependency variant is not a single file', e)
            }
        })
    }

    abstract @OutputFile RegularFileProperty getOutput()

    protected abstract @Inject ProviderFactory getProviders()
}
