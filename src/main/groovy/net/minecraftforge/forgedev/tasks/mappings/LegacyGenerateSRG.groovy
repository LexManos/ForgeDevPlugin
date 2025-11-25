/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mappings


import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.minecraftforge.forgedev.ForgeDevTask
import net.minecraftforge.forgedev.Tools
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.srgutils.IMappingFile.INode
import net.minecraftforge.srgutils.IRenamer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

@CompileStatic
abstract class LegacyGenerateSRG extends DefaultTask implements ForgeDevTask {
    abstract @Input Property<IMappingFile.Format> getFormat()
    abstract @Input Property<Boolean> getNotch()
    abstract @Input Property<Boolean> getReverse()

    abstract @InputFile RegularFileProperty getMcpSrgData()
    abstract @InputFile RegularFileProperty getMappingsZip()
    abstract @OutputFile RegularFileProperty getOutput()

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getWorkerClasspath()
    protected abstract @Inject WorkerExecutor getWorkerExecutor()

    LegacyGenerateSRG() {
        this.workerClasspath.from(
            this.getTool(Tools.SRGUTILS),
            this.getTool(Tools.FASTCSV)
        )

        this.format.convention(IMappingFile.Format.TSRG2)
        this.notch.convention(false)
        this.reverse.convention(false)

        this.output.convention(this.getDefaultOutputFile('tsrg'))
    }

    @TaskAction
    protected void exec() {
        final work = this.workerExecutor.classLoaderIsolation {
            it.classpath.from(this.workerClasspath)
        }

        work.submit(Action) {
            it.format.set this.format
            it.notch.set this.notch
            it.reverse.set this.reverse

            it.mcpSrgData.set this.mcpSrgData
            it.mappingsZip.set this.mappingsZip
            it.output.set this.output
        }

        work.await()
    }

    @CompileStatic
    @PackageScope static abstract class Action implements WorkAction<Parameters> {
        @CompileStatic
        static interface Parameters extends WorkParameters {
            Property<IMappingFile.Format> getFormat()
            Property<Boolean> getNotch()
            Property<Boolean> getReverse()

            RegularFileProperty getMcpSrgData()
            RegularFileProperty getMappingsZip()
            RegularFileProperty getOutput()
        }

        @Inject
        Action() {}

        @Override
        void execute() {
            // Obf -> Srg
            var input = IMappingFile.load(this.parameters.mcpSrgData.get().asFile)

            // Srg->Obf->Srg = Srg->Srg
            boolean notch = this.parameters.notch.getOrElse(false)
            if (!notch)
                input = input.reverse().chain(input)

            var map = MCPNames.load(this.parameters.mappingsZip.get().asFile)
            // Srg->Mapped
            var ret = input.rename(renamer(map))

            ret.write(
                this.parameters.output.get().asFile.toPath(),
                this.parameters.format.get(),
                this.parameters.reverse.getOrElse(false)
            )
        }

        private static IRenamer renamer(MCPNames map) throws IOException {
            new IRenamer() {
                String rename(IMappingFile.INode value) {
                    final mapped = value.mapped
                    map.names().getOrDefault(mapped, mapped)
                }

                @Override
                String rename(IMappingFile.IPackage value) {
                    this.rename(value as INode)
                }

                @Override
                String rename(IMappingFile.IClass value) {
                    this.rename(value as INode)
                }

                @Override
                String rename(IMappingFile.IField value) {
                    this.rename(value as INode)
                }

                @Override
                String rename(IMappingFile.IMethod value) {
                    this.rename(value as INode)
                }

                @Override
                String rename(IMappingFile.IParameter value) {
                    this.rename(value as INode)
                }
            }
        }
    }
}
