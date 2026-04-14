/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.specs.Spec;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class Util extends SharedUtil {
    private Util() { }

    public static final Spec<String> IS_NOT_BLANK = s -> !s.isBlank();

    public static String getProjectEclipseName(Project project) {
        var eclipse = project.getExtensions().findByType(EclipseModel.class);
        var name = eclipse == null ? null : eclipse.getProject().getName();
        return name != null ? name : project.getName();
    }

    public static void logFiles(Logger logger, String prefix, Collection<File> files) {
        var itr = files.iterator();
        if (itr.hasNext())
            logger.info("{}: null", prefix);
        else {
            var padding = " ".repeat(prefix.length());
            logger.info("{}: {}", prefix, itr.next().getAbsolutePath());
            while (itr.hasNext())
                logger.info("{}: {}", padding, itr.next().getAbsolutePath());
        }
    }

    public static void logArgs(Logger logger, String prefix, List<String> args) {
        var padding = " ".repeat(prefix.length());
        for (int x = 0; x < args.size(); x++) {
            var current = args.get(x);
            var next = args.size() > x + 1 ? args.get(x + 1) : null;
            var line = current;
            if (current.startsWith("--") && next != null && !next.startsWith("--")) {
                x++;
                line += ' ' + next;
            }
            logger.info("{}{}", x == 0 ? prefix : padding, line);
        }
    }

    @SuppressWarnings("unchecked")
    public static <R, E extends Throwable> R sneak(Throwable e) throws E {
        throw (E)e;
    }

    private static final Action<?> DO_NOTHING = input -> {};
    @SuppressWarnings("unchecked")
    static <T> Action<T> noop() {
        return (Action<T>)DO_NOTHING;
    }

    static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1);
    }
}
