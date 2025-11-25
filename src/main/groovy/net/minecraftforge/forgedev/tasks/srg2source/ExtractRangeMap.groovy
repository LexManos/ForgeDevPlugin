/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.srg2source

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.jvm.toolchain.JavaLanguageVersion

import javax.inject.Inject

@CompileStatic
abstract class ExtractRangeMap extends S2SExec {
    abstract @InputFiles @Optional ConfigurableFileCollection getDependencies()
    abstract @InputFiles ConfigurableFileCollection getSources()
    abstract @OutputFile RegularFileProperty getOutput()

    abstract @Input @Optional Property<Boolean> getBatch()
    abstract @Input @Optional Property<Boolean> getMixins()
    abstract @Input @Optional Property<Boolean> getMixinsFatal()

    private final Property<JavaVersion> sourceCompatiblityProp = this.objects.property(JavaVersion)

    @Input @Optional Property<JavaVersion> getSourceCompatibility() {
        this.sourceCompatiblityProp
    }

    void setSourceCompatibility(JavaLanguageVersion javaVersion) {
        this.sourceCompatibility.set(JavaVersion.toVersion(javaVersion))
    }

    void setSourceCompatibility(Provider<? extends JavaLanguageVersion> javaVersion) {
        this.sourceCompatibility.set(javaVersion.map(JavaVersion.&toVersion))
    }

    @Inject
    ExtractRangeMap() {
        this.output.convention(this.getDefaultOutputFile('txt'))

        this.sourceCompatibility.convention(
            this.project.extensions.findByType(JavaPluginExtension).toolchain.languageVersion.map(JavaVersion.&toVersion)
        )
    }

    @Override
    protected void addArguments() {
        this.args('--extract')

        this.args('--lib', this.dependencies)
        this.args('--in', this.sources)
        this.args('--out', this.output)

        this.args('--batch', this.batch)
        this.args('--mixins', this.mixins)
        this.args('--fatalmixins', this.mixinsFatal)

        this.args('--source-compatibility', this.sourceCompatibility.map(this.&parseSourceCompatibility))

        super.addArguments()
    }
}
