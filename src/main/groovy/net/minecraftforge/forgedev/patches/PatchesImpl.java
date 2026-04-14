/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.patches;

import net.minecraftforge.forgedev.ForgeDevExtension;
import net.minecraftforge.forgedev.base.PatcherBase;
import net.minecraftforge.forgedev.tasks.patching.diff.ApplyPatches;
import net.minecraftforge.forgedev.tasks.patching.diff.GeneratePatches;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

public abstract class PatchesImpl implements Patches {
    private final String name;
    private final TaskProvider<ApplyPatches> apply;
    private final TaskProvider<GeneratePatches> make;

    private PatcherBase base;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public PatchesImpl(ForgeDevExtension extension, String name, TaskContainer tasks) {
        this.name = name;
        this.apply = tasks.register("applyPatches", ApplyPatches.class);
        this.make = tasks.register("makePatches", GeneratePatches.class);
    }

    @Override
    public PatcherBase getBase() {
        return base;
    }
    @Override
    public void setBase(PatcherBase base) {
        this.base = base;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public TaskProvider<ApplyPatches> getApply() {
        return apply;
    }
    @Override
    public TaskProvider<GeneratePatches> getMake() {
        return make;
    }
}
