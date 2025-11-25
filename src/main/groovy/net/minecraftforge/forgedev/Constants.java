/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

/// The package-private constants used throughout ForgeGradle.
final class Constants {
    private Constants() { }

    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    static final String MAVENIZER_NAME = "mavenizer";
    static final String MAVENIZER_VERSION = "0.4.9";
    static final String MAVENIZER_DL_URL = FORGE_MAVEN + "net/minecraftforge/minecraft-mavenizer/" + MAVENIZER_VERSION + "/minecraft-mavenizer-" + MAVENIZER_VERSION + ".jar";
    static final String MAVENIZER_MAIN = "net.minecraftforge.mcmaven.cli.Main";
    static final int MAVENIZER_JAVA = 25;

    static final String DIFFPATCH_NAME = "diffpatch";
    static final String DIFFPATCH_VERSION = "2.1.0.42";
    static final String DIFFPATCH_DL_URL = MAVEN_CENTRAL + "io/codechicken/DiffPatch/" + DIFFPATCH_VERSION + "/DiffPatch-" + DIFFPATCH_VERSION + "-all.jar";
    static final String DIFFPATCH_MAIN = "io.codechicken.diffpatch.cli.DiffPatchCli";
    static final int DIFFPATCH_JAVA = 8;

    static final String BINPATCH_NAME = "binpatcher";
    static final String BINPATCH_VERSION = "1.2.2";
    static final String BINPATCH_DL_URL = FORGE_MAVEN + "net/minecraftforge/binarypatcher/" + BINPATCH_VERSION + "/binarypatcher-" + BINPATCH_VERSION + "-fatjar.jar";
    static final String BINPATCH_MAIN = "net.minecraftforge.binarypatcher.ConsoleTool";
    static final int BINPATCH_JAVA = 8;

    static final String FART_NAME = "fart";
    static final String FART_VERSION = "1.1.1";
    static final String FART_DL_URL = FORGE_MAVEN + "net/minecraftforge/ForgeAutoRenamingTool/" + FART_VERSION + "/ForgeAutoRenamingTool-" + FART_VERSION + "-all.jar";
    static final String FART_MAIN = "net.minecraftforge.fart.Main";
    static final int FART_JAVA = 8;

    static final String SRG2SRC_NAME = "srg2source";
    static final String SRG2SRC_VERSION = "8.1.1";
    static final String SRG2SRC_DL_URL = FORGE_MAVEN + "net/minecraftforge/Srg2Source/" + SRG2SRC_VERSION + "/Srg2Source-" + SRG2SRC_VERSION + "-fatjar.jar";
    static final String SRG2SRC_MAIN = "net.minecraftforge.srg2source.ConsoleTool";
    static final int SRG2SRC_JAVA = 17;

    static final String INSTALLERTOOLS_NAME = "installertools";
    static final String INSTALLERTOOLS_VERSION = "1.4.4";
    static final String INSTALLERTOOLS_DL_URL = FORGE_MAVEN + "net/minecraftforge/installertools/" + INSTALLERTOOLS_VERSION + "/installertools-" + INSTALLERTOOLS_VERSION + "-fatjar.jar";
    static final String INSTALLERTOOLS_MAIN = "net.minecraftforge.installertools.ConsoleTool";
    static final int INSTALLERTOOLS_JAVA = 8;

    static final String JARCOMPATIBILITYCHECKER_NAME = "jarcompatibilitychecker";
    static final String JARCOMPATIBILITYCHECKER_VERSION = "0.1.28";
    static final String JARCOMPATIBILITYCHECKER_DL_URL = FORGE_MAVEN + "net/minecraftforge/JarCompatibilityChecker/" + JARCOMPATIBILITYCHECKER_VERSION + "/JarCompatibilityChecker-" + JARCOMPATIBILITYCHECKER_VERSION + "-all.jar";
    static final String JARCOMPATIBILITYCHECKER_MAIN = "net.minecraftforge.jarcompatibilitychecker.ConsoleTool";
    static final int JARCOMPATIBILITYCHECKER_JAVA = 8;

    static final String SRGUTILS_NAME = "srgutils";
    static final String SRGUTILS_VERSION = "0.5.14";
    static final String SRGUTILS_DL_URL = FORGE_MAVEN + "net/minecraftforge/srgutils/" + SRGUTILS_VERSION + "/srgutils-" + SRGUTILS_VERSION + ".jar";
    static final int SRGUTILS_JAVA = 8;

    static final String FASTCSV_NAME = "fastcsv";
    static final String FASTCSV_VERSION = "3.7.0";
    static final String FASTCSV_DL_URL = MAVEN_CENTRAL + "de/siegmar/fastcsv/" + FASTCSV_VERSION + "/fastcsv-" + FASTCSV_VERSION + ".jar";
    static final int FASTCSV_JAVA = 11;

    static final String SLIMELAUNCHER_NAME = "slimelauncher";
    static final String SLIMELAUNCHER_VERSION = "0.1.6";
    static final String SLIMELAUNCHER_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/slime-launcher/" + SLIMELAUNCHER_VERSION + "/slime-launcher-" + SLIMELAUNCHER_VERSION + ".jar";
    static final int SLIMELAUNCHER_JAVA_VERSION = 8;
    static final String SLIMELAUNCHER_MAIN = "net.minecraftforge.launcher.Main";
}
