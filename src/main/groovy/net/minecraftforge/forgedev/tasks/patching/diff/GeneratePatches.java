/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.io.File;

public abstract class GeneratePatches extends BaseDiffPatchExec {
    // region Modified ========================================================
    public abstract @InputFile @Optional RegularFileProperty getModifiedFile();
    public abstract @InputDirectory @Optional DirectoryProperty getModifiedDirectory();
    public void setModified(File file) {
        if (file.isDirectory()) {
            this.getModifiedFile().unset();
            this.getModifiedDirectory().set(file);
        } else {
            this.getModifiedFile().set(file);
            this.getModifiedDirectory().unset();
        }
    }
    public void setModified(RegularFileProperty file) {
        this.getModifiedFile().set(file);
        this.getModifiedDirectory().unset();
    }
    public void setModified(RegularFile file) {
        this.getModifiedFile().set(file);
        this.getModifiedDirectory().unset();
    }
    public void setModified(DirectoryProperty dir) {
        this.getModifiedFile().unset();
        this.getModifiedDirectory().set(dir);
    }
    public void setModified(Directory dir) {
        this.getModifiedFile().unset();
        this.getModifiedDirectory().set(dir);
    }
    // endregion

    // Diff specific
    public abstract @Input Property<Boolean> getDiff();
    public abstract @Input Property<Boolean> getAutoHeader();
    public abstract @Input @Optional Property<Integer> getContext();
    public abstract @Input @Optional Property<String> getArchiveModified();

    @Inject
    public GeneratePatches() {
        this.getDiff().convention(false);
        this.getAutoHeader().convention(false);
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
            "--diff",
            resolve("input", getInputFile(), getInputDirectory()),
            resolve("modified", getModifiedFile(), getModifiedDirectory())
        );
    }
}
