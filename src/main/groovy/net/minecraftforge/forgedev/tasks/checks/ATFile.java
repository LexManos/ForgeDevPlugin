/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import org.gradle.api.model.ObjectFactory;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public abstract class ATFile {
    private Map<String, Entry> entries;
    protected List<Entry> errors;

    @Inject
    public ATFile(Map<String, Entry> entries, List<Entry> errors) {
        this.entries = entries;
        this.errors = errors;
    }

    public Map<String, Entry> getEntries() {
        return this.entries;
    }

    static ATFile parse(ObjectFactory objects, File file) throws IOException {
        var lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        return parse(objects, lines);
    }

    static ATFile parse(ObjectFactory objects, List<String> lines) {
        var entries = new TreeMap<String, Entry>();
        var errors = new ArrayList<Entry>();

        Group group = null;
        for (var line : lines) {
            if (line.isEmpty()) continue;
            if (line.startsWith("#group ")) {
                var entry = objects.newInstance(Group.class, line.substring(7));

                if (!entry.desc.equals("*") && !entry.desc.equals("*()") && !entry.desc.equals("<init>"))
                    entry.errors.add("Invalid group: " + line);

                group = entry;

                var old = entries.put(entry.key, entry);
                if (old != null)
                    entry.errors.add("Duplicate group: " + line);
            } else if (group != null) {
                if (line.startsWith("#endgroup")) {
                    group = null;
                } else {
                    var entry = objects.newInstance(Entry.class, line);
                    group.existing.add(entry.key);
                }
            } else if (line.startsWith("#endgroup")) {
                var entry = objects.newInstance(Entry.class, line);
                entry.errors.add("Invalid group ending: " + line);
                errors.add(entry);
            } else if (line.startsWith("#")) {
                //Nom
            } else {
                var entry = objects.newInstance(Entry.class, line);
                var old =  entries.put(entry.key, entry);
                if (old != null)
                    entry.errors.add("Duplicate entry: " + line);
            }
        }
        return objects.newInstance(ATFile.class, entries, errors);
    }

    public static abstract class Entry {
        public final String line;
        public final String key;
        public final String modifier;
        public final String cls;
        public final String desc;
        public final @Nullable String comment;
        final List<String> errors = new ArrayList<>();

        @Inject
        public Entry(String line) {
            this.line = line;
            var idx = line.indexOf('#');
            if (idx == -1) {
                this.comment = null;
            } else {
                this.comment = line.substring(idx);
                line = line.substring(0, idx - 1);
            }
            var data = (line.trim() + "     ").split(" ", -1);

            this.modifier = data[0];
            // Convert to Source names, internal names are fine by spec, but not supported by old AST based AT implementations.
            this.cls = data[1].replace('/', '.');
            this.desc = data[2];
            this.key = desc.isEmpty() ? cls : cls + ' ' + desc;
        }

        @Nullable
        public Group asGroup() {
            return null;
        }

        public int strength() {
            if (modifier.endsWith("-f") || modifier.endsWith("+f")) return 4;
            return switch (modifier.toLowerCase(Locale.ENGLISH)) {
                case "public" -> 3;
                case "protected" -> 2;
                case "default" -> 1;
                case "private" -> 0;
                default -> -1;
            };
        }

        public boolean isForced() {
            return comment != null && comment.startsWith("#force ");
        }
    }

    public static abstract class Group extends Entry {
        public final Set<String> existing = new LinkedHashSet<>();
        public final Set<String> children = new TreeSet<>();

        @Inject
        public Group(String line) {
            super(line);
        }

        @Override
        @Nullable
        public Group asGroup() {
            return this;
        }
    }
}
