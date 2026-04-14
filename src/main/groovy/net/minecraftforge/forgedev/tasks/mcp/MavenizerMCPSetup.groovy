/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mcp

import groovy.transform.CompileStatic
import net.minecraftforge.forgedev.ForgeDevTask
import net.minecraftforge.util.data.json.JsonData
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecResult

import javax.inject.Inject

@CompileStatic
abstract class MavenizerMCPSetup extends MavenizerMCPTask {
    abstract @InputFile @Optional RegularFileProperty getAccessTransformerConfig()
    abstract @InputFile @Optional RegularFileProperty getSideAnnotationStripperConfig()
    abstract @Input @Optional Property<String> getMappings();

    protected abstract @OutputFile RegularFileProperty getSetupFiles()

    abstract @OutputFile RegularFileProperty getVersionManifest()
    abstract @OutputFile RegularFileProperty getVersionJson()
    abstract @OutputFile RegularFileProperty getClientRaw()
    abstract @OutputFile RegularFileProperty getClientMappings()
    abstract @OutputFile RegularFileProperty getServerRaw()
    abstract @OutputFile RegularFileProperty getServerExtracted()
    abstract @OutputFile RegularFileProperty getServerMappings()
    abstract @OutputFile RegularFileProperty getLibrariesList()

    @Inject
    MavenizerMCPSetup() {
        setupFiles.convention(this.defaultOutputDirectory.map { it.file('setup_files.json') })

        versionManifest.convention(this.defaultOutputDirectory.map { it.file('manifest.json') })
        versionJson.convention(this.defaultOutputDirectory.map { it.file('version.json') })
        clientRaw.convention(this.defaultOutputDirectory.map { it.file('client.jar') })
        clientMappings.convention(this.defaultOutputDirectory.map { it.file('client_mappings.txt') })
        serverRaw.convention(this.defaultOutputDirectory.map { it.file('server_bundled.jar') })
        serverExtracted.convention(this.defaultOutputDirectory.map { it.file('server.jar') })
        serverMappings.convention(this.defaultOutputDirectory.map { it.file('server_mappings.txt') })
        librariesList.convention(this.defaultOutputDirectory.map { it.file('libraries.txt') })
    }

    @Override
    protected void addArguments() {
        super.addArguments()

        if (this.mappings.isPresent())
            this.args('--mappings', this.mappings)

        this.args('--at', this.accessTransformerConfig)
        this.args('--sas', this.sideAnnotationStripperConfig)

        this.args('--output-files', this.setupFiles)
    }

    @Override
    protected ExecResult exec() {
        var result = super.exec()

        var setupFiles = JsonData.fromJson(this.setupFiles.asFile.get(), MCPSetupFiles)
        versionManifest.asFile.get().bytes = new File(setupFiles.versionManifest).bytes
        versionJson.asFile.get().bytes = new File(setupFiles.versionJson).bytes
        clientRaw.asFile.get().bytes = new File(setupFiles.clientRaw).bytes
        clientMappings.asFile.get().bytes = new File(setupFiles.clientMappings).bytes
        serverRaw.asFile.get().bytes = new File(setupFiles.serverRaw).bytes
        serverExtracted.asFile.get().bytes = new File(setupFiles.serverExtracted).bytes
        serverMappings.asFile.get().bytes = new File(setupFiles.serverMappings).bytes
        librariesList.asFile.get().bytes = new File(setupFiles.librariesList).bytes

        return result
    }
}
