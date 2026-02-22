/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.api.specs.Spec;

public final class Util extends SharedUtil {
    private Util() { }

    public static final Spec<String> IS_NOT_BLANK = s -> !s.isBlank();
}
