/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.patching.diff;

import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.forgedev.tasks.ToolExec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;

import javax.inject.Inject;
import java.io.File;

public abstract class BaseDiffPatchExec extends ToolExec {
    /* CLI FLAGS - See io.codechicken.diffpatch.cli.DiffPatchCli#mainI, or run --help on the fat jar */

    // Utility
    public abstract @Input @Console Property<Boolean> getVerbose();
    public abstract @Input @Optional @Console Property<String> getLogLevel();
    public abstract @Input @Console Property<Boolean> getSummary();

    // God I hate gradle, all this to allow either a file or directory to be specified
    // region input ===========================================================
    public abstract @InputFile @Optional RegularFileProperty getInputFile();
    public abstract @InputDirectory @Optional DirectoryProperty getInputDirectory();
    public void setInput(File file) {
        if (file.isDirectory()) {
            this.getInputFile().unset();
            this.getInputDirectory().set(file);
        } else {
            this.getInputFile().set(file);
            this.getInputDirectory().unset();
        }
    }
    public void setInput(RegularFileProperty file) {
        this.getInputFile().set(file);
        this.getInputDirectory().unset();
    }
    public void setInput(RegularFile file) {
        this.getInputFile().set(file);
        this.getInputDirectory().unset();
    }
    public void setInput(DirectoryProperty dir) {
        this.getInputFile().unset();
        this.getInputDirectory().set(dir);
    }
    public void setInput(Directory dir) {
        this.getInputFile().unset();
        this.getInputDirectory().set(dir);
    }
    // endregion
    // region output ==========================================================
    public abstract @OutputFile @Optional RegularFileProperty getOutputFile();
    public abstract @OutputDirectory @Optional DirectoryProperty getOutputDirectory();
    public void setOutput(File file) {
        if (file.isDirectory()) {
            this.getOutputFile().unset();
            this.getOutputDirectory().set(file);
        } else {
            this.getOutputFile().set(file);
            this.getOutputDirectory().unset();
        }
    }
    public void setOutput(RegularFileProperty file) {
        this.getOutputFile().set(file);
        this.getOutputDirectory().unset();
    }
    public void setOutput(RegularFile file) {
        this.getOutputFile().set(file);
        this.getOutputDirectory().unset();
    }
    public void setOutput(DirectoryProperty dir) {
        this.getOutputFile().unset();
        this.getOutputDirectory().set(dir);
    }
    public void setOutput(Directory dir) {
        this.getOutputFile().unset();
        this.getOutputDirectory().set(dir);
    }
    // endregion

    public abstract @Input @Optional Property<String> getArchive();
    public abstract @Input @Optional Property<String> getArchiveBase();
    public abstract @Input @Optional Property<String> getBasePathPrefix();
    public abstract @Input @Optional Property<String> getModifiedPathPrefix();
    public abstract @Input @Optional Property<String> getLineEndings();

    @Inject
    protected BaseDiffPatchExec() {
        super(Tools.DIFFPATCH);
        this.getVerbose().convention(false);
        this.getSummary().convention(false);
    }

    protected void addArguments() {
        //region Utility
        if (this.getVerbose().get())
            this.args("--verbose");
        if (this.getLogLevel().isPresent())
            this.args("--log-level", this.getLogLevel().get());
        if (this.getSummary().get())
            this.args("--summary");
        //endregion

        //region Shared
        this.args("--output", resolve("output", getOutputFile(), getOutputDirectory()));

        if (this.getArchive().isPresent())
            this.args("--archive", this.getArchive().get());
        if (this.getArchiveBase().isPresent())
            this.args("--archive-base", this.getArchiveBase().get());
        if (this.getBasePathPrefix().isPresent())
            this.args("--base-path-prefix", this.getBasePathPrefix().get());
        if (this.getModifiedPathPrefix().isPresent())
            this.args("--modified-path-prefix", this.getModifiedPathPrefix().get());
        if (this.getLineEndings().isPresent()) {
            this.args("--line-endings", this.getLineEndings().map(val -> switch (val) {
                case "\r" -> "CR";
                case "\n" -> "LF";
                case "\r\n" -> "CRLF";
                default -> val;
            }).get());
        }
        //endregion

        super.addArguments();
    }

    protected File resolve(String name, RegularFileProperty file, DirectoryProperty dir) {
        if (file.isPresent() && dir.isPresent())
            throw new IllegalStateException("Can not specify both directory and file value for " + name);
        return (file.isPresent() ? file : dir).map(this.getProblems().ensureFileLocation()).get().getAsFile();
    }
}
