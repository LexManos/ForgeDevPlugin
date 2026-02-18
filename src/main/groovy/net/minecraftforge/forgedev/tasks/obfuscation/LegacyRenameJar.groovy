/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.obfuscation

import groovy.transform.CompileStatic
import net.minecraftforge.forgedev.Tools
import net.minecraftforge.forgedev.Util
import net.minecraftforge.forgedev.tasks.ToolExec
import net.minecraftforge.srgutils.IMappingFile
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@CompileStatic
@Deprecated(forRemoval = true)
@SuppressWarnings('GrDeprecatedAPIUsage')
abstract class LegacyRenameJar extends ToolExec {
    private static final Logger LOGGER = Logging.getLogger(LegacyRenameJar)

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getWorkerActionClasspath()

    abstract @InputFile RegularFileProperty getMappings()
    abstract @InputFiles @Optional ConfigurableFileCollection getExtraMappings()
    protected abstract @Internal RegularFileProperty getTemporaryMappings()

    abstract @InputFile RegularFileProperty getInput()
    abstract @OutputFile RegularFileProperty getOutput()
    abstract @InputFiles @Classpath @Optional ConfigurableFileCollection getLibraries()

    protected abstract @Inject WorkerExecutor getWorkerExecutor()

    @Inject
    LegacyRenameJar() {
        super(Tools.RENAMER)

        this.preferToolchainJvm.set(true)

        this.workerActionClasspath.from(
            this.getTool(Tools.SRGUTILS).classpath
        )

        this.temporaryMappings.convention(this.defaultOutputDirectory.map { it.file('mappings_temp.tsrg') })
        this.standardOutputLogLevel.set(LogLevel.INFO)
        this.standardErrorLogLevel.set(LogLevel.INFO)
    }

    @Override
    protected void addArguments() {
        super.addArguments()

        var argsList = [
            '--input', this.input.asFile.get().absolutePath,
            '--names', this.temporaryMappings.asFile.get().absolutePath,
            '--output', this.output.asFile.get().absolutePath
        ]

        for (var library in this.libraries.files) {
            argsList.add('--lib')
            argsList.add(library.absolutePath)
        }

        var argsFile = new File(this.temporaryDir, 'args.txt')
        Files.write(argsFile.toPath(), argsList, StandardCharsets.UTF_8)

        this.args('--cfg', argsFile.absolutePath)
    }

    @Override
    protected ExecResult exec() {
        final work = this.workerExecutor.classLoaderIsolation {
            it.classpath.from(this.workerActionClasspath)
        }

        work.submit(Action) {
            it.mappings.set(this.mappings)
            it.extraMappings.setFrom(this.extraMappings)
            it.temporaryMappings.set(this.temporaryMappings)
        }

        work.await()

        return super.exec()
    }

    @CompileStatic
    protected static abstract class Action implements WorkAction<Parameters> {
        @CompileStatic
        static interface Parameters extends WorkParameters {
            RegularFileProperty getMappings()
            ConfigurableFileCollection getExtraMappings()
            RegularFileProperty getTemporaryMappings()
        }

        @Inject
        Action() {}

        @Override
        void execute() {
            var tempMappings = this.parameters.temporaryMappings.asFile.get()
            if (tempMappings.getParentFile() != null && !tempMappings.getParentFile().exists() && !tempMappings.getParentFile().mkdirs()) {
                LOGGER.warn("WARNING: Could not create parent directories for temp dir '{}'", tempMappings.getAbsolutePath())
            }

            if (tempMappings.exists() && !tempMappings.delete()) {
                throw new IllegalStateException("Could not delete temp mappings file: " + tempMappings.getAbsolutePath())
            } else {
                var mappings = IMappingFile.load(this.parameters.mappings.asFile.get())

                for(var file : this.parameters.extraMappings.files) {
                    mappings = mappings.merge(IMappingFile.load(file))
                }

                mappings.write(tempMappings.toPath(), IMappingFile.Format.TSRG2, false)
            }
        }
    }
}
