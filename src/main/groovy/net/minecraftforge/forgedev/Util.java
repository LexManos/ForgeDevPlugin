/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.Project;
import org.gradle.api.specs.Spec;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

public final class Util extends SharedUtil {
    private Util() { }

    public static final Spec<String> IS_NOT_BLANK = s -> !s.isBlank();

    public static String getProjectEclipseName(Project project) {
        var eclipse = project.getExtensions().findByType(EclipseModel.class);
        var name = eclipse == null ? null : eclipse.getProject().getName();
        return name != null ? name : project.getName();
    }
}
