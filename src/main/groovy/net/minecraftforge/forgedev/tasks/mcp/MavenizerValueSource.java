/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.mcp;

import net.minecraftforge.forgedev.ForgeDevPlugin;
import net.minecraftforge.forgedev.ForgeDevProblems;
import net.minecraftforge.forgedev.Tools;
import net.minecraftforge.gradleutils.shared.Tool;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

abstract class MavenizerValueSource<T, P extends MavenizerValueSource.Parameters> implements ValueSource<T, P> {
    interface Parameters extends ValueSourceParameters {
        default void init(ForgeDevPlugin plugin, ForgeDevProblems problems) {
            var tool = plugin.getTool(Tools.MAVENIZER);

            getClasspath().convention(tool.getClasspath());
            getJavaLauncher().convention(tool.getJavaLauncher().map(j -> j.getExecutablePath().getAsFile().getAbsolutePath()));
            getCaches().convention(plugin.globalCaches().dir(tool.getName().toLowerCase(Locale.ENGLISH) + "/cache").map(problems.ensureFileLocation()));
        }

        Property<String> getName();

        ConfigurableFileCollection getClasspath();

        Property<String> getJavaLauncher();

        DirectoryProperty getCaches();
    }

    private static final Logger LOGGER = Logging.getLogger(MavenizerValueSource.class);

    protected abstract @Inject ExecOperations getExecOperations();

    @Inject
    public MavenizerValueSource() { }

    protected int exec() {
        return this.getExecOperations().javaexec(spec -> {
            var params = this.getParameters();
            spec.setClasspath(params.getClasspath());
            spec.setExecutable(params.getJavaLauncher().get());
            {
                var args = new ArrayList<String>();
                this.addArguments(args);
                spec.setArgs(args);
            }

            LOGGER.info("Executing Mavenizer: ");
            var itr = params.getClasspath().iterator();
            LOGGER.info("  Classpath: {}", itr.next().getAbsolutePath());
            while (itr.hasNext())
                LOGGER.info("             {}", itr.next().getAbsolutePath());

            LOGGER.info("  Java: {}", params.getJavaLauncher().get());
            var args = spec.getArgs();
            var prefix = "  Arguments: ";
            for (int x = 0; x < args.size(); x++) {
                var current = args.get(x);
                var next = args.size() > x + 1 ? args.get(x + 1) : null;
                var line = current;
                if (current.startsWith("--") && next != null && !next.startsWith("--")) {
                    x++;
                    line += ' ' + next;
                }
                LOGGER.info("{}{}", prefix, line);
                prefix = "             ";
            }
        }).rethrowFailure().assertNormalExitValue().getExitValue();
    }

    protected void addArguments(List<String> args) {
        var parameters = getParameters();
        args.addAll(List.of(
            "--cache", parameters.getCaches().get().getAsFile().getAbsolutePath(),
            "--jdk-cache", parameters.getCaches().dir("jdks").get().getAsFile().getAbsolutePath()
        ));
    }
}
