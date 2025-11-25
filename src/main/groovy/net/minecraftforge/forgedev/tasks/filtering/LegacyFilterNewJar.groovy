/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.filtering

import groovy.transform.CompileStatic
import net.minecraftforge.forgedev.ForgeDevTask
import net.minecraftforge.forgedev.Tools
import net.minecraftforge.srgutils.IMappingFile
import net.minecraftforge.util.file.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@CompileStatic
abstract class LegacyFilterNewJar extends DefaultTask implements ForgeDevTask {
    abstract @InputFile RegularFileProperty getInput()
    abstract @InputFile RegularFileProperty getSrg()
    abstract @InputFiles ConfigurableFileCollection getBlacklist()
    abstract @OutputFile RegularFileProperty getOutput()

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getWorkerClasspath()
    protected abstract @Inject WorkerExecutor getWorkerExecutor()

    @Inject
    LegacyFilterNewJar() {
        this.output.convention(this.defaultOutputFile)

        this.workerClasspath.from(this.getTool(Tools.SRGUTILS))
    }

    @TaskAction
    protected void exec() {
        final work = this.workerExecutor.classLoaderIsolation {
            it.classpath.from(this.workerClasspath)
        }

        work.submit(Action) {
            it.input.set(this.input)
            it.srg.set(this.srg)
            it.blacklist.setFrom(this.blacklist)
            it.output.set(this.output)
        }

        work.await()
    }

    //We pack all inner classes in binpatches. So strip anything thats a vanilla class or inner class of one.
    private static boolean isVanilla(Set<String> classes, String cls) {
        int idx = cls.indexOf('$')
        if (idx != -1) {
            return isVanilla(classes, cls.substring(0, idx))
        }
        return classes.contains(cls)
    }

    @CompileStatic
    protected static abstract class Action implements WorkAction<Parameters> {
        @CompileStatic
        static interface Parameters extends WorkParameters {
            RegularFileProperty getInput()

            RegularFileProperty getSrg()

            ConfigurableFileCollection getBlacklist()

            RegularFileProperty getOutput()
        }

        @Override
        void execute() {
            var filter = new HashSet<String>()
            for (var file : this.parameters.blacklist.files) {
                try (var zip = new ZipFile(file)) {
                    Iterable<ZipEntry> entries = zip.entries().&asIterator
                    for (var entry in entries) {
                        filter.add(entry.getName())
                    }
                }
            }

            var classes = IMappingFile.load(this.parameters.srg.asFile.get()).classes.collect(new HashSet<String>(), IMappingFile.IClass.&getMapped)

            try (var zin = new ZipFile(this.parameters.input.asFile.get())
                 var out = new ZipOutputStream(new FileOutputStream(this.parameters.output.asFile.get()))) {

                Iterable<ZipEntry> entries = zin.entries().&asIterator
                for (var entry in entries) {
                    if (entry.directory || filter.contains(entry.name) ||
                        (entry.name.endsWith(".class") && isVanilla(classes, entry.name.substring(0, entry.name.length() - 6)))) {
                        continue
                    }
                    out.putNextEntry(FileUtils.getStableEntry(entry.name))
                    zin.getInputStream(entry).transferTo(out)
                    out.closeEntry()
                }
            }
        }
    }
}
