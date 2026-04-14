/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.process.ExecResult;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public abstract class ApplyPatches extends BaseDiffPatchExec {
    // region Patches =========================================================
    public abstract @InputFile @Optional RegularFileProperty getPatchesFile();
    public abstract @InputDirectory @Optional DirectoryProperty getPatchesDirectory();
    public void setPatches(File file) {
        if (file.isDirectory()) {
            this.getPatchesFile().unset();
            this.getPatchesDirectory().set(file);
        } else {
            this.getPatchesFile().set(file);
            this.getPatchesDirectory().unset();
        }
    }
    public void setPatches(RegularFile file) {
        this.getPatchesFile().set(file);
        this.getPatchesDirectory().unset();
    }
    public void setPatches(DirectoryProperty dir) {
        this.getPatchesFile().unset();
        this.getPatchesDirectory().set(dir);
    }
    public void setPatches(Directory dir) {
        this.getPatchesFile().unset();
        this.getPatchesDirectory().set(dir);
    }
    // endregion
    // region Rejects =========================================================
    public abstract @OutputFile @Optional RegularFileProperty getRejectsFile();
    public abstract @OutputDirectory @Optional DirectoryProperty getRejectsDirectory();
    public void setRejects(File file) {
        if (file.isDirectory()) {
            this.getRejectsFile().unset();
            this.getRejectsDirectory().set(file);
        } else {
            this.getRejectsFile().set(file);
            this.getRejectsDirectory().unset();
        }
    }
    public void setRejects(RegularFileProperty file) {
        this.getRejectsFile().set(file);
        this.getRejectsDirectory().unset();
    }
    public void setRejects(RegularFile file) {
        this.getRejectsFile().set(file);
        this.getRejectsDirectory().unset();
    }
    public void setRejects(DirectoryProperty dir) {
        this.getRejectsFile().unset();
        this.getRejectsDirectory().set(dir);
    }
    public void setRejects(Directory dir) {
        this.getRejectsFile().unset();
        this.getRejectsDirectory().set(dir);
    }
    // endregion

    // Patch specific
    public abstract @Input Property<Boolean> getFailOnError();
    public abstract @Input @Optional Property<String> getArchiveRejects();
    public abstract @Input @Optional Property<Float> getFuzz();
    public abstract @Input @Optional Property<Integer> getOffset();
    public abstract @Input @Optional Property<String> getMode();
    public abstract @Input @Optional Property<String> getArchivePatches();

    // Patch shared
    public abstract @Input @Optional Property<String> getPrefix();

    @Inject
    public ApplyPatches() {
        this.getStandardOutputLogLevel().convention(LogLevel.WARN);
        this.getFailOnError().convention(true);
        this.getLogLevel().convention("warn");
        this.getMode().convention("access");
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        //region Patch specific
        if (this.getRejectsFile().isPresent() || this.getRejectsDirectory().isPresent())
            this.args("--reject", resolve("rejects", getRejectsFile(), getRejectsDirectory()));
        if (this.getArchiveRejects().isPresent())
            this.args("--archive-rejects", this.getArchiveRejects().get());
        if (this.getFuzz().isPresent())
            this.args("--fuzz", this.getFuzz().get());
        if (this.getOffset().isPresent())
            this.args("--offset", this.getOffset().get());
        if (this.getMode().isPresent())
            this.args("--mode", this.getMode().get());
        if (this.getArchivePatches().isPresent())
            this.args("--archive-patches", this.getArchivePatches().get());
        //endregion

        //region Patch shared
        if (this.getPrefix().isPresent())
            this.args("--prefix", this.getPrefix().get());
        //endregion

        //region Patch Task
        // https://github.com/TheCBProject/DiffPatch/blob/204d393ee23f5cd4298f771c7b9157ee21eb3b62/src/main/java/io/codechicken/diffpatch/cli/DiffPatchCli.java#L191
        // --patch {base} {patches}
        this.args(
            "--patch",
            resolve("input", getInputFile(), getInputDirectory()),
            resolve("patches", getPatchesFile(), getPatchesDirectory())
        );
        //endregion
    }

    @Override
    protected @Nullable ExecResult exec() throws IOException {
        // No patches, not sure when this would ever come up, but isn't hard to support
        if (!this.getPatchesFile().isPresent() && !this.getPatchesDirectory().isPresent()) {
            var output = resolve("output",  getOutputFile(), getOutputDirectory());
            var input =  resolve("input",  getInputFile(), getInputDirectory());
            if (output.getParent() != null)
                Files.createDirectories(output.getParentFile().toPath());
            Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return null;
        }

        var result = super.exec();

        var exitValue = result.getExitValue();
        if (exitValue != 0) {
            // patches failed
            if (exitValue != 1)
                result.rethrowFailure();

            // some other error
            if (this.getFailOnError().get())
                result.assertNormalExitValue();
        }
        return result;
    }
}
