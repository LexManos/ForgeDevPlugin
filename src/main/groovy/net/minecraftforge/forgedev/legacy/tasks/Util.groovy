/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.legacy.tasks

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.minecraftforge.forgedev.legacy.values.LibraryInfo
import net.minecraftforge.forgedev.legacy.values.MinimalResolvedArtifact
import net.minecraftforge.gradleutils.shared.SharedUtil
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.provider.HasConfigurableValue
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@CompileStatic
final class Util {
    public static final int ASM_LEVEL = Opcodes.ASM9
    private static final HttpClient HTTP = HttpClient.newBuilder().build()

    /**
     * @deprecated Meta-programming like this is highly discouraged since it hurts IDE linting and is very hard to maintain. Move to either using static method or static Kotlin extensions if using Kotlin DSL.
     */
    @Deprecated(forRemoval = true)
    static void init() {
        UtilExtensions.init()
    }

    @CompileStatic
    @Deprecated(forRemoval = true)
    static Map getArtifacts(Configuration config) {
        var ret = [:]
        var semaphore = new Semaphore(1, true)
        config.resolvedConfiguration.resolvedArtifacts.parallelStream().forEachOrdered(dep -> {
            var info = getMavenInfoFromDep(dep)
            var domain = 'libraries.minecraft.net'
            var url = "https://$domain/$info.path"
            if (!checkExists(url))
                url.values[0] = 'maven.minecraftforge.net'

            var sha1 = sha1(dep.file)

            semaphore.acquire()
            ret[info.key] = [
                name: info.name,
                downloads: [
                    artifact: [
                        path: info.path,
                        url: url.toString(),
                        sha1: sha1,
                        size: dep.file.length()
                    ]
                ]
            ]
            semaphore.release()
        })
        return ret
    }

    @CompileDynamic
    @Deprecated(forRemoval = true)
    static String[] getClasspath(Project project, Map libs, String artifact) {
        def ret = []
        artifactTreeOld(project, artifact).each { key, lib ->
            libs[lib.name] = lib
            if (lib.name != artifact)
                ret.add(lib.name)
        }
        return ret
    }

    @CompileDynamic
    static String[] getClasspath(Project project, MapProperty<String, LibraryInfo> libs, String artifact) {
        var ret = []
        artifactTree(project, artifact).get().forEach { key, lib ->
            libs.put(lib.name(), lib)
            if (lib.name() != artifact)
                ret.add(lib.name())
        }
        return ret
    }

    @CompileDynamic
    static String[] getClasspath(Project project, SetProperty<MinimalResolvedArtifact> libs, String artifact) {
        var ret = []
        var artifacts = artifactTree(project, artifact).get()
        var libraries = LibraryInfo.from(artifacts.values())
        libraries.forEach { key, lib ->
            libs.add(artifacts.get(lib.name()))
            if (lib.name() != artifact)
                ret.add(lib.name())
        }
        return ret
    }

    @CompileStatic
    static Map<String, ?> getMavenInfoFromDep(ResolvedArtifact dep) {
        return getMavenInfoFromMap([
            group: dep.moduleVersion.id.group,
            name: dep.moduleVersion.id.name,
            version: dep.moduleVersion.id.version,
            classifier: dep.classifier,
            extension: dep.extension
        ])
    }

    static Map<String, ?> getMavenInfoFromTask(AbstractArchiveTask task) {
        return getMavenInfoFromMap([
            group: task.project.group.toString(),
            name: task.project.name,
            version: task.project.version.toString(),
            classifier: task.archiveClassifier.get(),
            extension: task.archiveExtension.get()
        ])
    }

    static Map<String, ?> getMavenInfoFromTask(Task task, String classifier) {
        return getMavenInfoFromMap([
            group: task.project.group.toString(),
            name: task.project.name,
            version: task.project.version.toString(),
            classifier: classifier,
            extension: 'jar'
        ])
    }

    private static Map<String, ?> getMavenInfoFromMap(Map<String, String> art) {
        var key = "$art.group:$art.name"
        var name = "$art.group:$art.name:$art.version"
        var path = "${art.group.replace('.', '/')}/$art.name/$art.version/$art.name-$art.version"
        if (art.classifier !== null) {
            name += ":$art.classifier"
            path += "-$art.classifier"
        }
        if ('jar' != art.extension) {
            name += "@$art.extension"
            path += ".$art.extension"
        } else {
            path += ".jar"
        }
        return [
            key: key.toString(),
            name: name.toString(),
            path: path.toString(),
            art: art
        ]
    }

    @CompileDynamic
    static String iso8601Now() { new Date().iso8601() }

    static String sha1(File file) {
        MessageDigest md = MessageDigest.getInstance('SHA-1')
        file.eachByte(4096) { byte[] bytes, int size ->
            md.update(bytes, 0, size)
        }
        return md.digest().collect(this.&toHex).join('')
    }
    static String sha256(File file) {
        MessageDigest md = MessageDigest.getInstance('SHA-256')
        file.eachByte(4096) { byte[] bytes, int size ->
            md.update(bytes, 0, size)
        }
        return md.digest().collect(this.&toHex).join('')
    }

    @PackageScope static String toHex(byte bite) {
        return String.format('%02x', bite)
    }

    @CompileDynamic
    static Provider<Map<String, MinimalResolvedArtifact>> artifactTree(Project project, String artifact, boolean transitive = true) {
        return MinimalResolvedArtifact.from(project, project.configurations.detachedConfiguration(
            project.dependencies.create(artifact)
        ).tap { it.transitive = transitive }).map { list ->
            var map = new LinkedHashMap<String, MinimalResolvedArtifact>(list.size())
            for (var minimal in list) {
                map.put(minimal.info().key(), minimal)
            }
            return map
        }
    }

    @CompileDynamic
    @Deprecated(forRemoval = true)
    private static Map artifactTreeOld(Project project, String artifact, boolean transitive = true) {
        if (!project.ext.has('tree_resolver'))
            project.ext.tree_resolver = 1
        def cfg = project.configurations.create('tree_resolver_' + project.ext.tree_resolver++)
        cfg.transitive = transitive
        def dep = project.dependencies.create(artifact)
        cfg.dependencies.add(dep)
        def files = cfg.resolve()
        return getArtifacts(cfg)
    }

    static boolean checkExists(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(new URI(url))
                    .method('HEAD', HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding()
            ).statusCode() === 200
        } catch (Exception e) {
            if (e.toString().contains('unable to find valid certification path to requested target'))
                throw new RuntimeException("Failed to connect to $url: Missing certificate root authority, try updating Java")
            throw e
        }
    }

    @CompileDynamic
    static String getLatestForgeVersion(String mcVersion) {
        final json = new JsonSlurper().parseText(new URL('https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json').getText('UTF-8'))
        final ver = json.promos["$mcVersion-latest"]
        ver === null ? null : (mcVersion + '-' + ver)
    }

    static void processClassNodes(File file, @ClosureParams(value = SimpleType, options = 'org.objectweb.asm.tree.ClassNode') Closure process) {
        file.withInputStream { i ->
            new ZipInputStream(i).withCloseable { zin ->
                ZipEntry zein
                while ((zein = zin.nextEntry) !== null) {
                    if (zein.name.endsWith('.class')) {
                        var node = new ClassNode(ASM_LEVEL)
                        new ClassReader(zin).accept(node, 0)
                        process(node)
                    }
                }
            }
        }
    }

    @CompileDynamic
    static String asArtifactString(Object artifact) {
        def value = SharedUtil.unpack(artifact)
        if (!(value instanceof Dependency))
            throw new IllegalArgumentException("Cannot get non-dependency as artifact string! Found: $value.class")

        def classifier = value.hasProperty('classifier') ? ":$value.classifier" : ''
        def extension = value.hasProperty('artifactType') ? "@$value.artifactType" : value.hasProperty('extension') ? "@$value.extension" : ''
        classifier = classifier != ':null' ? classifier : ''
        extension = extension != '@null' ? extension : ''

        "$value.group:$value.name:$value.version$classifier$extension".toString()
    }

    static <R extends HasConfigurableValue> R finalize(Project project, R ret) {
        ret.disallowChanges();
        ret.finalizeValueOnRead();
        return ret;
    }

    static String capitalize(String s) {
        return s.capitalize();
    }

    static String kebab(String s) {
        s.replaceAll('([A-Z])', '-$1').toLowerCase()
    }

    private static final Action<?> DO_NOTHING = input -> {};
    @SuppressWarnings("unchecked")
    static <T> Action<T> noop() {
        return (Action<T>)DO_NOTHING;
    }
}
