/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.gradleutils.shared.Tool;

public final class Tools {
    private Tools() { }

    // EXECUTABLE
    public static final Tool MAVENIZER = Tool.ofForge("mavenizer", "net.minecraftforge:minecraft-mavenizer:0.4.9", 25, "net.minecraftforge.mcmaven.cli.Main");
    public static final Tool DIFFPATCH = Tool.of("diffpatch", "io.codechicken:DiffPatch:2.1.0.42:all", Constants.MAVEN_CENTRAL, 8);
    public static final Tool BINPATCH = Tool.ofForge("binpatcher", "net.minecraftforge:binarypatcher:1.2.2:fatjar", 8);
    public static final Tool INSTALLERTOOLS = Tool.ofForge("installertools", "net.minecraftforge:installertools:1.4.4:fatjar", 8);
    public static final Tool JARCOMPATIBILITYCHECKER = Tool.ofForge("jarcompatibilitychecker", "net.minecraftforge:JarCompatibilityChecker:0.1.28:all", 8);
    public static final Tool RENAMER = Tool.ofForge("renamer", "net.minecraftforge:ForgeAutoRenamingTool:1.1.1:all", 8);
    public static final Tool SRG2SRC = Tool.ofForge("srg2source", "net.minecraftforge:Srg2Source:8.1.1:fatjar", 17);
    public static final Tool SLIMELAUNCHER = Tool.ofForge("slimelauncher", "net.minecraftforge:slime-launcher:0.1.8", 8, "net.minecraftforge.launcher.Main");

    // LIBRARIES
    public static final Tool SRGUTILS = Tool.ofForge("srgutils", "net.minecraftforge:srgutils:0.5.14", 8);
    public static final Tool FASTCSV = Tool.of("fastcsv", "de.siegmar:fastcsv:3.7.0", Constants.MAVEN_CENTRAL, 11);
}
