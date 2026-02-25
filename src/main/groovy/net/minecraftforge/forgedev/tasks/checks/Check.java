/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.checks;

import org.gradle.api.Action;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;

public class Check<T extends CheckTask> {
    private final TaskProvider<T> check;
    private final TaskProvider<T> fix;

    @Inject
    public Check(TaskProvider<T> check, TaskProvider<T> fix) {
        this.check = check;
        this.fix = fix;
    }

    public TaskProvider<T> getCheck() {
        return check;
    }
    public void check(Action<T> action) {
        getCheck().configure(action);
    }
    public TaskProvider<T> getFix() {
        return fix;
    }
    public void fix(Action<T> action) {
        getFix().configure(action);
    }
}
