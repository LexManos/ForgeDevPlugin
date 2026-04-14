/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import net.minecraftforge.forgedev.Util;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.IOException;

public abstract class GeneratePatches extends BaseDiffPatchExec {
    public abstract @InputFiles ConfigurableFileCollection getModified();
    public abstract @OutputFile RegularFileProperty getOutput();
    public abstract @Optional @OutputDirectory DirectoryProperty getOutputDirectory();

    // Diff specific
    public abstract @Input Property<Boolean> getDiff();
    public abstract @Input Property<Boolean> getAutoHeader();
    public abstract @Input @Optional Property<Integer> getContext();
    public abstract @Input @Optional Property<String> getArchiveModified();

    @Inject
    public GeneratePatches() {
        this.getDiff().convention(false);
        this.getAutoHeader().convention(false);
        this.getOutput().convention(this.getDefaultOutputFile());
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        //region Diff specific
        if (this.getAutoHeader().get())
            this.args("--auto-header");
        if (this.getContext().isPresent())
            this.args("--context", this.getContext().get());
        if (this.getArchiveModified().isPresent())
            this.args("--archive-modified", this.getArchiveModified().get());
        //endregion

        // https://github.com/TheCBProject/DiffPatch/blob/204d393ee23f5cd4298f771c7b9157ee21eb3b62/src/main/java/io/codechicken/diffpatch/cli/DiffPatchCli.java#L155
        // --diff {base} {modified}
        this.args(
            "--output", this.getOutput().getAsFile().get(),
            "--diff",
            this.getInput().getSingleFile(),
            this.getModified().getSingleFile()
        );
    }

    @Override
    protected @Nullable ExecResult exec() throws IOException {
        var result = super.exec().assertNormalExitValue().rethrowFailure();
        var output = getOutput().getAsFile().get();
        if (this.getOutputDirectory().isPresent())
            Util.extractZip(output, this.getOutputDirectory().getAsFile().get(), true);
        return result;
    }
}
