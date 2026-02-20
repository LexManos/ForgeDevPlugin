/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks;

import org.gradle.api.file.RegularFileProperty;

public interface SingleFileOutput {
    RegularFileProperty getOutput();
}
