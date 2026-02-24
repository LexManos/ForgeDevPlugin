/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installertools

import groovy.transform.CompileStatic
import net.minecraftforge.forgedev.Tools
import net.minecraftforge.forgedev.tasks.ToolExec
import net.minecraftforge.gradleutils.shared.Tool
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult

import javax.inject.Inject

@CompileStatic
abstract class ExtractInheritance extends ToolExec {
    abstract @InputFile RegularFileProperty getInput();

    abstract @InputFiles @Classpath ConfigurableFileCollection getLibraries();

    abstract @OutputFile RegularFileProperty getOutput();

    @Inject
    ExtractInheritance() {
        super(Tools.INSTALLERTOOLS)
        this.output.convention(this.getDefaultOutputFile('json'))
        this.preferToolchainJvm.convention(true)
        this.standardOutputLogLevel.convention(LogLevel.INFO);
    }

    @Override
    protected ExecResult exec() throws IOException {
        super.exec().assertNormalExitValue().rethrowFailure()
    }

    @Override
    protected void addArguments() {
        this.args(
            '--task', 'extract_inheritance',
            '--input', this.input.asFile.get().absolutePath,
            '--output', this.output.asFile.get().absolutePath
        )

        this.args('--lib', this.libraries)

        super.addArguments()
    }
}
