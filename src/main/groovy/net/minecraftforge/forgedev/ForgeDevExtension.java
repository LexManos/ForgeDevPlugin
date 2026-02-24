/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev;

import net.minecraftforge.forgedev.legacy.tasks.Util;
import net.minecraftforge.forgedev.legacy.values.CIRuntime;
import net.minecraftforge.forgedev.tasks.checks.CheckTask;
import net.minecraftforge.forgedev.tasks.compat.LegacyExtractZip;
import net.minecraftforge.forgedev.tasks.compat.LegacyMergeFilesTask;
import net.minecraftforge.forgedev.tasks.filtering.LegacyFilterNewJar;
import net.minecraftforge.forgedev.tasks.generation.GeneratePatcherConfigV2;
import net.minecraftforge.forgedev.tasks.installer.Installer;
import net.minecraftforge.forgedev.tasks.installertools.DownloadMappings;
import net.minecraftforge.forgedev.tasks.launcher.SlimeLauncherExec;
import net.minecraftforge.forgedev.tasks.mappings.LegacyApplyMappings;
import net.minecraftforge.forgedev.tasks.mappings.LegacyGenerateSRG;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerMCPDataTask;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerMCPMavenValue;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerMCPSetup;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerRawArtifact;
import net.minecraftforge.forgedev.tasks.mcp.MavenizerSyncMappingsValue;
import net.minecraftforge.forgedev.tasks.obfuscation.LegacyReobfuscateJar;
import net.minecraftforge.forgedev.tasks.patching.binary.CreateBinPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.BakePatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import net.minecraftforge.forgedev.tasks.sas.CreateFakeSASPatches;
import net.minecraftforge.forgedev.tasks.shim.Shim;
import net.minecraftforge.forgedev.tasks.srg2source.ApplyRangeMap;
import net.minecraftforge.forgedev.tasks.srg2source.ExtractRangeMap;
import net.minecraftforge.gradleutils.shared.Closures;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jetbrains.annotations.VisibleForTesting;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Function;

// TODO [ForgeDev] Hide this and make a public interface
@VisibleForTesting
public abstract class ForgeDevExtension {
    public static final String NAME = "forgedev";

    private static final Attribute<String> OS = Attribute.of("net.minecraftforge.native.operatingSystem", String.class);
    private static final Attribute<String> MAPPINGS_CHANNEL = Attribute.of("net.minecraftforge.mappings.channel", String.class);
    private static final Attribute<String> MAPPINGS_VERSION = Attribute.of("net.minecraftforge.mappings.version", String.class);

    private final ForgeDevProblems problems = this.getObjects().newInstance(ForgeDevProblems.class);

    private final DirectoryProperty mavenizerRepo = this.getObjects().directoryProperty();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ProjectLayout getProjectLayout();

    private final Project project;
    private final Provider<Boolean> isCi;

    @Inject
    public ForgeDevExtension(ForgeDevPlugin plugin, Project project) {
        this.mavenizerRepo.set(plugin.globalCaches().dir("repo").map(this.problems.ensureFileLocation()));
        this.project = project;
        this.isCi = getProviders().of(CIRuntime.class, it -> {});
        this.setup(plugin, project);
    }

    // NOTE: Pass into RepositoryHandler#maven
    public Action<? super MavenArtifactRepository> getMavenizer() {
        return repo -> {
            repo.setName("Mavenizer");
            repo.setUrl(this.mavenizerRepo);
        };
    }

    @VisibleForTesting
    public DirectoryProperty getMavenizerRepo() {
        return this.mavenizerRepo;
    }

    public boolean isCi() {
        return this.isCi.get();
    }

    private Configuration minecraftDepsConfiguration;
    public Configuration getMinecraftConfiguration() {
        return this.minecraftDepsConfiguration;
    }
    // Tasks creation helpers

    // =====================================================================
    public Installer installer() {
        return installer(Installer.DEFAULT_NAME);
    }
    public Installer installer(Action<Installer> action) {
        return installer(Installer.DEFAULT_NAME, action);
    }
    public Installer installer(String name) {
        return installer(name, Util.noop());
    }
    public Installer installer(String name, Action<Installer> action) {
        var ret = Installer.register(this.project, this, name);
        action.execute(ret);
        return ret;
    }
    // =====================================================================

    // =====================================================================
    // 'Check' and 'Fix' tasks, Registers the same task twice with a 'fix'
    // boolean input. Registers the 'check{Name}' version to the 'check' group
    // and the 'checkAndFix{Name}' task to the 'checkAndFix' group.
    // =====================================================================
    public <T extends CheckTask> void check(String taskName, Class<T> clazz) {
        check(taskName, clazz, Util.noop());
    }
    public <T extends CheckTask> void check(String taskName, Class<T> clazz, Action<? super T> action) {
        var tasks = this.project.getTasks();
        taskName = StringGroovyMethods.capitalize(taskName);

        var check = tasks.register("check" + taskName, clazz, task -> {
            action.execute(task);
            task.getFix().set(false);
        });
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(check));

        var checkAndFix = tasks.register("checkAndFix" + taskName, clazz, task -> {
            action.execute(task);
            task.getFix().set(true);
        });
        tasks.named("checkAndFix", task -> task.dependsOn(checkAndFix));
    }
    // ===============================================================================

    public Shim shim() {
        return shim(Util.noop());
    }
    public Shim shim(Action<Shim> action) {
        return shim(Shim.DEFAULT_NAME, action);
    }
    public Shim shim(String name) {
        return shim(name, Util.noop());
    }
    public Shim shim(String name, Action<Shim> action) {
        var ret = Shim.register(this.project, this, name);
        action.execute(ret);
        return ret;
    }
    // ===============================================================================




    @SuppressWarnings("removal")
    private void setup(ForgeDevPlugin plugin, Project project) {
        var tasks = project.getTasks();

        var legacyPatcher = project.getExtensions().create(LegacyPatcherExtension.EXTENSION_NAME, LegacyPatcherExtension.class);
        var legacyMcp = project.getExtensions().create(LegacyMCPExtension.EXTENSION_NAME, LegacyMCPExtension.class, plugin);
        var java = project.getExtensions().getByType(JavaPluginExtension.class);

        var jar = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        var compileJava = tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class);
        var main = java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);

        // needs to exist because it's currently referenced in the buildscript
        // TODO STOP DOING THAT SHIT
        var setupMCP = tasks.register("setupMCP", MavenizerMCPSetup.class);
        var setupMCPSrg = tasks.register("setupMCPSrg", MavenizerMCPSetup.class, task -> task.getRename().set(false));

        var downloadClientMappings = tasks.register("downloadClientMappings", DownloadMappings.class, task -> task.getSide().set("client"));
        var downloadServerMappings = tasks.register("downloadServerMappings", DownloadMappings.class, task -> task.getSide().set("server"));

        minecraftDepsConfiguration = project.getConfigurations().detachedConfiguration();
        var mappingsConfiguration = project.getConfigurations().detachedConfiguration();

        var applyPatches = tasks.register("applyPatches", ApplyPatches.class, task -> {
            final Provider<Directory> workDir = project.getLayout().getBuildDirectory().dir(task.getName());
            task.getOutput().set(workDir.map(s -> s.file("output.zip")));
            task.getArchive().set("zip");
            task.setRejects(workDir.map(s -> s.file("rejects.zip")));
            task.getArchiveRejects().set("zip");
            task.getPatches().set(legacyPatcher.getPatches());
            task.getMode().set("access");
            if (project.hasProperty("UPDATING")) {
                task.getMode().set("fuzzy");
                task.setRejects(project.getLayout().getProjectDirectory().dir("rejects"));
                task.getArchiveRejects().unset();
                task.getFailOnError().set(false);
            }
        });

        var toMCPConfig = tasks.register("srg2mcp", LegacyApplyMappings.class, task -> {
            task.getInput().set(applyPatches.flatMap(ApplyPatches::getOutput));
            task.getMappings().setFrom(mappingsConfiguration);
            task.getLambdas().set(false);
        });
        var extractMapped = tasks.register("extractMapped", LegacyExtractZip.class, task -> {
            task.getInput().set(toMCPConfig.flatMap(LegacyApplyMappings::getOutput));
            task.getOutput().set(legacyPatcher.getPatchedSrc());
        });

        var extractRangeMap = tasks.register("extractRangeMap", ExtractRangeMap.class, task -> {
            task.getDependencies().from(jar.flatMap(Jar::getArchiveFile));

            // Only add main source, as we inject the patchedSrc into it as a sourceset.
            task.getSources().from(main.map(s -> s.getJava().getSourceDirectories()));
            task.getDependencies().from(compileJava.map(JavaCompile::getClasspath));
        });

        var createMcp2Srg = tasks.register("createMcp2Srg", LegacyGenerateSRG.class, task -> {
            task.getReverse().set(true);
            task.getOutput().set(task.getOutputFile("mcp2srg.tsrg"));
        });
        var createSrg2Mcp = tasks.register("createSrg2Mcp", LegacyGenerateSRG.class, task -> {
            task.getReverse().set(false);
            task.getOutput().set(task.getOutputFile("srg2mcp.tsrg"));
        });
        var createMcp2Obf = tasks.register("createMcp2Obf", LegacyGenerateSRG.class, task -> {
            task.getNotch().set(true);
            task.getReverse().set(true);
            task.getOutput().set(task.getOutputFile("mcp2obf.tsrg"));
        });

        // TODO DOES NOTHING!
        var createExc = tasks.register("createExc");

        var applyRangeMap = tasks.register("applyRangeMap", ApplyRangeMap.class, task -> {
            task.getSources().from(main.map(s -> s.getJava().getSourceDirectories().minus(project.files(legacyPatcher.getPatchedSrc()))));
            task.setOnlyIf(t -> !((ApplyRangeMap) t).getSources().isEmpty());
            task.getRangeMap().set(extractRangeMap.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(LegacyGenerateSRG::getOutput));
            task.getExcFiles().from(/*createExc.flatMap(CreateExc::getOutput), */legacyPatcher.getExcs());
            task.getKeepImports().set(true);
        });

        var applyRangeMapBase = tasks.register("applyRangeMapBase", ApplyRangeMap.class, task -> {
            task.setOnlyIf(t -> legacyPatcher.getPatches().isPresent());
            task.getSources().from(legacyPatcher.getPatchedSrc());
            task.getRangeMap().set(extractRangeMap.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(LegacyGenerateSRG::getOutput));
            task.getExcFiles().from(/*createExc.flatMap(CreateExc::getOutput), */legacyPatcher.getExcs());
            task.getKeepImports().set(true);
        });

        var userdevConfig = tasks.register("userdevConfig", GeneratePatcherConfigV2.class);

        var genPatches = tasks.register("genPatches", GeneratePatches.class, task -> {
            task.setOnlyIf(t -> legacyPatcher.getPatches().isPresent());
            task.getOutput().set(legacyPatcher.getPatches());
        });

        var bakePatches = tasks.register("bakePatches", BakePatches.class, task -> {
            task.dependsOn(genPatches);
            task.getInput().set(legacyPatcher.getPatches());
            task.getOutput().set(new File(task.getTemporaryDir(), "output.zip"));
        });

        var reobfJar = tasks.register("reobfJar", LegacyReobfuscateJar.class, task -> {
            task.getInput().set(jar.flatMap(Jar::getArchiveFile));
            task.getLibraries().from(minecraftDepsConfiguration);
            task.getOutput().convention(task.getDefaultOutputFile());
        });

        var genJoinedBinPatches = tasks.register("genJoinedBinPatches", CreateBinPatches.class, task -> {
            task.getCreate().from(reobfJar.flatMap(LegacyReobfuscateJar::getOutput));
            task.getOutput().convention(project.getLayout().getBuildDirectory().dir(task.getName()).map(d -> d.file("joined.lzma")));
        });
        var genClientBinPatches = tasks.register("genClientBinPatches", CreateBinPatches.class, task -> {
            task.getCreate().from(reobfJar.flatMap(LegacyReobfuscateJar::getOutput));
            task.getOutput().convention(project.getLayout().getBuildDirectory().dir(task.getName()).map(d -> d.file("client.lzma")));
        });
        var genServerBinPatches = tasks.register("genServerBinPatches", CreateBinPatches.class, task -> {
            task.getCreate().from(reobfJar.flatMap(LegacyReobfuscateJar::getOutput));
            task.getOutput().convention(project.getLayout().getBuildDirectory().dir(task.getName()).map(d -> d.file("server.lzma")));
        });
        var genBinPatchesTasks = List.of(genJoinedBinPatches, genClientBinPatches, genServerBinPatches);
        var genBinPatches = tasks.register("genBinPatches", task -> task.dependsOn(genJoinedBinPatches, genClientBinPatches, genServerBinPatches));

        var filterNew = tasks.register("filterJarNew", LegacyFilterNewJar.class, task -> task.getInput().set(reobfJar.flatMap(LegacyReobfuscateJar::getOutput)));

        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        // TODO This may conflict with normal sources jar if enabled
        //      Remember that we want to generalize ForgeDev to be used by both Forge and ForgeLoader
        var srgSourcesJar = tasks.register("legacySourcesJar", Jar.class, task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.setOnlyIf(t -> applyRangeMap.flatMap(ApplyRangeMap::getOutput).map(rf -> rf.getAsFile().exists()).getOrElse(false));
            task.dependsOn(applyRangeMap);
            task.from(project.zipTree(applyRangeMap.flatMap(ApplyRangeMap::getOutput)));
            task.getArchiveClassifier().set("sources");
        });

        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        var universalJar = tasks.register("universalJar", Jar.class, task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.dependsOn(filterNew);
            task.from(project.zipTree(filterNew.flatMap(LegacyFilterNewJar::getOutput)));
            task.from(java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getResources));
            task.getArchiveClassifier().set("universal");
        });

        /*UserDev:
         * config.json
         * joined.lzma
         * sources.jar
         * patches/
         *   net/minecraft/item/Item.java.patch
         * ats/
         *   at1.cfg
         *   at2.cfg
         */
        var userdevJar = tasks.register("userdevJar", Jar.class, task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.dependsOn(srgSourcesJar, bakePatches);
            task.setOnlyIf(t -> legacyPatcher.isSrgPatches());
            task.from(userdevConfig.flatMap(GeneratePatcherConfigV2::getOutput), e -> e.rename(f -> "config.json"));
            task.from(genJoinedBinPatches.flatMap(CreateBinPatches::getOutput), e -> e.rename(f -> "joined.lzma"));
            task.from(project.zipTree(bakePatches.flatMap(BakePatches::getOutput)), e -> e.into("patches/"));
            task.getArchiveClassifier().set("userdev");
        });
        var assemble = tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, task ->
            task.dependsOn(universalJar, userdevJar)
        );

        var sourceSetsDir = this.getObjects().directoryProperty().value(this.getProjectLayout().getBuildDirectory().dir("sourceSets"));
        var mergeSourceSets = this.problems.test("net.minecraftforge.gradle.merge-source-sets");
        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().configureEach(sourceSet -> {
            if (mergeSourceSets) {
                // This is documented in SourceSetOutput's javadoc comment
                var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                sourceSet.getOutput().setResourcesDir(unifiedDir);
                sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
            }

            project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                var eclipse = project.getExtensions().getByType(EclipseModel.class);
                if (mergeSourceSets)
                    eclipse.getClasspath().setDefaultOutputDir(sourceSetsDir.getAsFile().get());
                else
                    System.out.println("WARNING: Source set will not be merged for " + sourceSet.getName() + "!");
            });
        });

        project.afterEvaluate(p -> {
            downloadClientMappings.configure(task -> task.getVersion().set(legacyPatcher.getMappingVersion()));
            downloadServerMappings.configure(task -> task.getVersion().set(legacyPatcher.getMappingVersion()));

            // TODO Add mappings as a dependency to FG7???
            // Add mappings so that it can be used by reflection tools.
            // net.minecraft:mappings_CHANNEL:VERSION@zip
            var mappingsDependency = getProviders().zip(
                getProviders().of(MavenizerSyncMappingsValue.class, spec -> spec.parameters(parameters -> {
                    parameters.init(plugin, problems);
                    parameters.getOutput().set(mavenizerRepo);
                    parameters.getVersion().set(legacyPatcher.getMappingVersion());
                })),
                getProviders().zip(legacyPatcher.getMappingChannel(), legacyPatcher.getMappingVersion(), (c, v) -> c + ':' + v),
                (ret, version) -> {
                return project.getDependencies().create(
                    "net.minecraft:mappings_%s@zip".formatted(version)
                );
            });
            var minecraftDependency = getProviders().zip(
                getProviders().of(MavenizerMCPMavenValue.class, spec -> spec.parameters(parameters -> {
                    parameters.init(plugin, problems);
                    parameters.getOutput().set(mavenizerRepo);
                    parameters.getArtifact().set(legacyMcp.getVersion());
                })),
                legacyMcp.getVersion(),
                (ret, version) -> {
                    return project.getDependencies().create(
                        "net.minecraft:joined:" + version,
                        Closures.<ExternalModuleDependency>consumer(dependency -> {
                            dependency.attributes(a -> {
                                a.attributeProvider(OS, getProviders().of(OSValueSource.class, spec -> { }));
                                a.attributeProvider(MAPPINGS_CHANNEL, legacyPatcher.getMappingChannel());
                                a.attributeProvider(MAPPINGS_VERSION, legacyPatcher.getMappingVersion());
                            });
                        })
                    );
                }
            );
            var minecraftExtraDependency = getProviders().zip(
                getProviders().of(MavenizerMCPMavenValue.class, spec -> spec.parameters(parameters -> {
                    parameters.init(plugin, problems);
                    parameters.getOutput().set(mavenizerRepo);
                    parameters.getArtifact().set(legacyMcp.getVersion().map(v -> "net.minecraft:client-extra:" + v));
                })),
                legacyMcp.getVersion(),
                (ret, version) -> {
                    return project.getDependencies().create(
                        "net.minecraft:client-extra:" + version,
                        Closures.<ExternalModuleDependency>consumer(dependency -> dependency.setTransitive(false))
                    );
                }
            );
            //syncMavenizer.configure(task -> task.getArtifact().set(legacyMcp.getVersion()));
            //syncMavenizerForExtra.configure(task -> task.getArtifact().set(legacyMcp.getVersion().map(v -> "net.minecraft:client-extra:" + v)));
            //syncMappingsMaven.configure(task -> task.getVersion().set(legacyPatcher.getMappingVersion()));
            project.getDependencies().addProvider(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, minecraftDependency);
            project.getDependencies().addProvider(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, minecraftExtraDependency);
            project.getDependencies().addProvider(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, mappingsDependency);
            minecraftDepsConfiguration.withDependencies(d -> d.addLater(minecraftDependency));
            mappingsConfiguration.withDependencies(d -> d.addLater(mappingsDependency));

            // Add the patched source as a source dir during afterEvaluate, to not be overwritten by buildscripts
            main.configure(s -> s.getJava().srcDir(legacyPatcher.getPatchedSrc()));

            // Automatically create the patches folder if it does not exist
            if (legacyPatcher.getPatches().isPresent()) {
                try {
                    Files.createDirectories(legacyPatcher.getPatches().get().getAsFile().toPath());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create patches folder", e);
                }
                srgSourcesJar.configure(task -> task.from(genPatches.flatMap(GeneratePatches::getOutput), copy -> copy.into("patches/")));
            }

            setupMCP.configure(task -> {
                task.getPipeline().set(legacyMcp.getPipeline());
                task.getArtifact().set(legacyMcp.getConfig());
            });
            setupMCPSrg.configure(task -> {
                task.getPipeline().set(legacyMcp.getPipeline());
                task.getArtifact().set(legacyMcp.getConfig());
            });
            legacyPatcher.getCleanSrc().set(setupMCP.flatMap(MavenizerMCPSetup::getOutput));
            applyPatches.configure(task -> task.getInput().convention(legacyPatcher.getCleanSrc()));
            genPatches.configure(task -> task.getInput().convention(legacyPatcher.getCleanSrc()));

            var extractSrg = tasks.register("extractSrg", MavenizerMCPDataTask.class, task -> {
                task.getArtifact().set(legacyMcp.getConfig());
                task.getOutput().convention(task.getOutputFile("obf2srg.tsrg"));
            });
            createMcp2Srg.configure(task -> task.getMcpSrgData().convention(extractSrg.flatMap(MavenizerMCPDataTask::getOutput)));

            // This was actually filtering the PARENT jar file. Since we don't support parent Patchers anymore, this is not needed.
            //filterNew.configure(task -> task.getBlacklist().from(jar.flatMap(AbstractArchiveTask::getArchiveFile)));

            tasks.withType(LegacyGenerateSRG.class, task -> {
                task.getMappings().setFrom(mappingsConfiguration);
            });

            createMcp2Obf.configure(task -> task.getMcpSrgData().convention(createMcp2Srg.flatMap(LegacyGenerateSRG::getMcpSrgData)));
            createSrg2Mcp.configure(task -> task.getMcpSrgData().convention(createMcp2Srg.flatMap(LegacyGenerateSRG::getMcpSrgData)));

            // TODO CLIENT EXTRA?
            if (!legacyPatcher.getAccessTransformers().isEmpty()) {
                var mergeATs = tasks.register("mergeATs", LegacyMergeFilesTask.class, task -> {
                    task.getFilesToMerge().setFrom(legacyPatcher.getAccessTransformers());

                    task.getOutput().set(project.getLayout().getBuildDirectory().file("legacy-forgedev/merged_ats.cfg"));
                });
                setupMCP.configure(task -> {
                    task.dependsOn(mergeATs);
                    task.getAccessTransformerConfig().set(mergeATs.flatMap(LegacyMergeFilesTask::getOutput));
                });
                setupMCPSrg.configure(task -> {
                    task.dependsOn(mergeATs);
                    task.getAccessTransformerConfig().set(mergeATs.flatMap(LegacyMergeFilesTask::getOutput));
                });
                for (var f : legacyPatcher.getAccessTransformers()) {
                    userdevJar.configure(t -> t.from(f, e -> e.into("ats/")));
                    userdevConfig.configure(t -> t.getATs().from(f));
                }
            }

            if (!legacyPatcher.getSideAnnotationStrippers().isEmpty()) {
                setupMCP.configure(task -> {
                    // TODO do this better
                    task.getSideAnnotationStripperConfig().fileProvider(getProviders().provider(() -> legacyPatcher.getSideAnnotationStrippers().getSingleFile()));
                });
                setupMCPSrg.configure(task -> {
                    // TODO do this better
                    task.getSideAnnotationStripperConfig().fileProvider(getProviders().provider(() -> legacyPatcher.getSideAnnotationStrippers().getSingleFile()));
                });

                userdevConfig.configure(task -> task.getSASs().from(legacyPatcher.getSideAnnotationStrippers()));
                for (var sas : legacyPatcher.getSideAnnotationStrippers()) {
                    userdevJar.configure(task -> task.from(sas, copy -> copy.into("sas/")));
                }

                var fakePatches = tasks.register("createFakeSASPatches", CreateFakeSASPatches.class, task ->
                    task.getFiles().from(legacyPatcher.getSideAnnotationStrippers())
                );
                for (var genBinPatchesTask : genBinPatchesTasks) {
                    genBinPatchesTask.configure(task -> task.getPatches().from(fakePatches.flatMap(CreateFakeSASPatches::getOutput)));
                }
            }

            if (!legacyPatcher.getExtraMappings().isEmpty()) {
                for (var extraMapping : legacyPatcher.getExtraMappings()) {
                    if (extraMapping instanceof File e) {
                        userdevJar.configure(t -> t.from(e, c -> c.into("srgs/")));
                        userdevConfig.configure(t -> t.getSRGs().from(e));
                    } else if (extraMapping instanceof String e) {
                        userdevConfig.configure(t -> t.getSRGLines().add(e));
                    }
                }
            }

            //UserDev Config Default Values
            userdevConfig.configure(task -> {
                task.getMCPConfig().set(legacyMcp.getConfig());
                task.getBinpatcherVersion().convention(Tools.BINPATCH.getModule().toString());
                task.getBinpatcherArguments().addAll("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");
                task.getUniversal().convention(universalJar.flatMap(t ->
                    t.getArchiveBaseName().flatMap(baseName ->
                        t.getArchiveClassifier().flatMap(classifier ->
                            t.getArchiveExtension().map(jarExt ->
                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                            )))));
                task.getSource().convention(srgSourcesJar.flatMap(t ->
                    t.getArchiveBaseName().flatMap(baseName ->
                        t.getArchiveClassifier().flatMap(classifier ->
                            t.getArchiveExtension().map(jarExt ->
                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                            )))));
                task.getPatchesOriginalPrefix().set(genPatches.flatMap(GeneratePatches::getBasePathPrefix));
                task.getPatchesModifiedPrefix().set(genPatches.flatMap(GeneratePatches::getModifiedPathPrefix));
                task.getNotchObf().set(legacyPatcher.getNotchObf());
            });

            if (legacyPatcher.isSrgPatches()) {
                genPatches.configure(task -> task.getModified().set(applyRangeMapBase.flatMap(ApplyRangeMap::getOutput)));
            } else {
                // Remap the 'clean' with out mappings.
                TaskProvider<LegacyApplyMappings> toMCPClean = tasks.register("srg2mcpClean", LegacyApplyMappings.class, task -> {
                    task.getInput().set(legacyPatcher.getCleanSrc());
                    task.getMappings().setFrom(mappingsConfiguration);
                    task.getLambdas().set(false);
                });

                var dirtyZip = tasks.register("patchedZip", Zip.class, task -> {
                    task.from(legacyPatcher.getPatchedSrc());
                    task.getArchiveFileName().set("output.zip");
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()));
                });

                // Fixup the inputs.
                applyPatches.configure(task -> {
                    task.dependsOn(toMCPClean);
                    task.getInput().set(toMCPClean.flatMap(LegacyApplyMappings::getOutput));
                });
                genPatches.configure(task -> {
                    task.getInput().set(toMCPClean.flatMap(LegacyApplyMappings::getOutput));
                    task.getModified().set(dirtyZip.flatMap(AbstractArchiveTask::getArchiveFile));
                });

                // don't remember why this is blocked off, but it was in FG6 so i'm keeping it in here for now as well
                {
                    var mcpConfigArtifact = legacyMcp.getConfig();
                    var srgNames = this.getProviders().provider(() -> !legacyPatcher.getNotchObf());

                    Function<String, TaskProvider<MavenizerRawArtifact>> rawJarTask = pipeline -> {
                        MavenizerRawArtifact.register(project, pipeline, mcpConfigArtifact, srgNames.map(b -> !b));
                        return MavenizerRawArtifact.register(project, pipeline, mcpConfigArtifact, srgNames);
                    };
                    var rawJoinedJar = rawJarTask.apply("joined");
                    var rawClientJar = rawJarTask.apply("client");
                    var rawServerJar = rawJarTask.apply("server");

                    var srg = legacyPatcher.getNotchObf() ? createMcp2Obf : createMcp2Srg;
                    reobfJar.configure(task -> task.getSrg().set(srg.flatMap(LegacyGenerateSRG::getOutput)));

                    genJoinedBinPatches.configure(task -> task.getClean().builtBy(rawJoinedJar));
                    genClientBinPatches.configure(task -> task.getClean().builtBy(rawClientJar));
                    genServerBinPatches.configure(task -> task.getClean().builtBy(rawServerJar));
                    tasks.withType(CreateBinPatches.class, task -> {
                        task.getSrg().from(srg.flatMap(LegacyGenerateSRG::getOutput));
                        if (legacyPatcher.getPatches().isPresent()) {
                            task.mustRunAfter(genPatches);
                            task.getPatches().from(legacyPatcher.getPatches());
                        }
                    });

                    filterNew.configure(task -> {
                        task.getSrg().set(srg.flatMap(LegacyGenerateSRG::getOutput));
                        task.getBlacklist().builtBy(rawJoinedJar);
                    });
                }
            }

            // TODO Clean up please
            record SimpleModuleIdentifier(String getGroup, String getName) implements ModuleIdentifier {
                SimpleModuleIdentifier() {
                    this("net.minecraft", "joined");
                }
            }

            if (!legacyPatcher.runs.isEmpty()) {
                var genEclipseRuns = project.getTasks().register("genEclipseRuns", task -> {
                    task.setGroup("IDE");
                    task.setDescription("Generates the run configuration launch files for Eclipse.");
                });

                File eclipseOutputDir;
                var eclipse = project.getExtensions().findByType(EclipseModel.class);
                if (eclipse != null) {
                    eclipse.synchronizationTasks(genEclipseRuns);
                    eclipseOutputDir = eclipse.getClasspath().getDefaultOutputDir();
                } else {
                    eclipseOutputDir = getProjectLayout().getProjectDirectory().dir("bin").getAsFile();
                }

                for (var sourceSet : List.of(main.get(), java.getSourceSets().named(SourceSet.TEST_SOURCE_SET_NAME).get())) {
                    legacyPatcher.runs.forEach(options -> {
                        var task = SlimeLauncherExec.register(project, sourceSet, options, new SimpleModuleIdentifier(), legacyMcp.getVersion().get(), eclipseOutputDir);
                    });
                }
            }
        });
    }
}
