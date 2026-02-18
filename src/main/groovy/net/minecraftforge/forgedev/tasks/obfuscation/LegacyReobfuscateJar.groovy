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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.process.ExecResult
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@CompileStatic
@Deprecated(forRemoval = true)
@SuppressWarnings('GrDeprecatedAPIUsage')
abstract class LegacyReobfuscateJar extends ToolExec {
    private static final Logger LOGGER = Logging.getLogger(LegacyReobfuscateJar)

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getWorkerActionClasspath()

    abstract @InputFile RegularFileProperty getInput()
    abstract @InputFile RegularFileProperty getSrg()
    abstract @OutputFile RegularFileProperty getOutput()
    abstract @InputFiles @Classpath @Optional ConfigurableFileCollection getLibraries()

    abstract @Input Property<Boolean> getKeepPackages()
    abstract @Input Property<Boolean> getKeepData()

    protected abstract @Inject WorkerExecutor getWorkerExecutor()

    @Inject
    LegacyReobfuscateJar() {
        super(Tools.RENAMER)

        this.preferToolchainJvm.set(true)

        this.workerActionClasspath.from(
            this.getTool(Tools.SRGUTILS).classpath
        )

        this.standardOutputLogLevel.set(LogLevel.INFO)

        this.keepPackages.convention(false)
        this.keepData.convention(false)
    }

    private File getTemporaryOutput() {
        return new File(this.temporaryDir, 'output.jar')
    }

    @Override
    protected void addArguments() {
        super.addArguments()

        var argsList = [
            '--input', this.input.get().asFile.absolutePath,
            '--map', this.srg.get().asFile.absolutePath,
            '--output', this.temporaryOutput.absolutePath
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
        var result = super.exec().rethrowFailure().assertNormalExitValue()

        final work = this.workerExecutor.classLoaderIsolation {
            it.classpath.from(this.workerActionClasspath)
        }

        work.submit(Action) {
            it.logLevel.set(standardOutputLogLevel)

            it.srg.set(this.srg)
            it.output.set(this.output)
            it.temporaryOutput.set(this.temporaryOutput)

            it.keepPackages.set(this.keepPackages)
            it.keepData.set(this.keepData)
        }

        work.await()

        return result
    }

    @CompileStatic
    protected static abstract class Action implements WorkAction<Parameters> {
        @CompileStatic
        static interface Parameters extends WorkParameters {
            Property<LogLevel> getLogLevel()

            RegularFileProperty getSrg()
            RegularFileProperty getOutput()
            RegularFileProperty getTemporaryOutput()

            Property<Boolean> getKeepPackages()
            Property<Boolean> getKeepData()
        }

        @Inject
        Action() {}

        @Override
        void execute() {
            var packages = new HashSet<String>()
            var srgMappings = IMappingFile.load(parameters.srg.asFile.get())
            for (IMappingFile.IClass srgClass : srgMappings.getClasses()) {
                String named = srgClass.getOriginal()
                int idx = named.lastIndexOf('/')
                if (idx != -1) {
                    packages.add(named.substring(0, idx + 1) + "package-info.class")
                }
            }

            var temporaryOutput = parameters.temporaryOutput.asFile.get()
            try (ZipFile zin = new ZipFile(temporaryOutput)
                 ZipOutputStream out = new ZipOutputStream(new FileOutputStream(parameters.output.asFile.get()))) {
                for (Enumeration<? extends ZipEntry> enu = zin.entries(); enu.hasMoreElements(); ) {
                    ZipEntry entry = enu.nextElement()
                    boolean filter = entry.isDirectory() || entry.getName().startsWith("mcp/") //Directories and MCP's annotations
                    if (!parameters.keepPackages.get()) filter |= packages.contains(entry.getName())
                    if (!parameters.keepData.get()) filter |= !entry.getName().endsWith(".class")

                    if (filter) {
                        LOGGER.log(parameters.logLevel.get(), "Filtered: {}", entry.getName())
                        continue
                    }
                    out.putNextEntry(entry)
                    zin.getInputStream(entry).transferTo(out)
                    out.closeEntry()
                }
            }

            if (!temporaryOutput.delete())
                LOGGER.warn('WARNING: Failed to delete temporary output: {}', temporaryOutput.absolutePath)
        }
    }
}
