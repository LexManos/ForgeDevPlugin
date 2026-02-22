/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.binary

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.minecraftforge.forgedev.Tools
import net.minecraftforge.forgedev.Util
import net.minecraftforge.forgedev.tasks.SingleFileOutput
import net.minecraftforge.forgedev.tasks.ToolExec
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.problems.Problems
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

import javax.inject.Inject

@CompileStatic
@PackageScope abstract class BinaryPatcherExec extends ToolExec implements SingleFileOutput {
    // Shared
    abstract @InputFiles ConfigurableFileCollection getClean()
    @Override
    abstract @OutputFile RegularFileProperty getOutput()
    abstract @Input @Optional ListProperty<String> getPrefix()
    abstract @Input Property<Boolean> getPack200()
    abstract @Deprecated @Input Property<Boolean> getLegacy()

    @Inject
    @SuppressWarnings('GrDeprecatedAPIUsage') // setting convention "false" for legacy
    BinaryPatcherExec() {
        super(Tools.BINPATCH)

        this.output.convention(this.defaultOutputFile)
        this.pack200.convention(false)
        this.legacy.convention(false)

        this.standardOutputLogLevel.set(LogLevel.INFO)
    }

    @Override
    protected void addArguments() {
        if (!this.clean.empty) {
            this.clean.forEach {
                this.args('--clean', it.absolutePath)
            }
        } else {
            throw new IllegalArgumentException('no clean!')
        }

        this.args('--output', this.output.get().asFile.absolutePath)

        if (this.prefix.present) {
            this.prefix.get().forEach {
                this.args('--prefix', it)
            }
        }

        if (this.pack200.getOrElse(false))
            this.args('--pack200')

        //noinspection GrDeprecatedAPIUsage
        if (this.legacy.getOrElse(false))
            this.args('--legacy')

        super.addArguments()
    }
}
