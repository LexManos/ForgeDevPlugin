/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.forgedev.tasks.installer.steps;

import net.minecraftforge.forgedev.tasks.installer.Tool;

import javax.inject.Inject;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Step implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Tool tool;
    public List<String> sides = List.of();

    protected List<String> args = new ArrayList<>();
    protected Map<String, String> outputs = new LinkedHashMap<>();

    @Inject
    public Step(Tool tool) {
        this.tool = tool;
    }

    public Tool getTool() {
        return this.tool;
    }
    public List<String> getSides() {
        return sides;
    }
    public List<String> getArgs() {
        return args;
    }
    public Map<String, String> getOutputs() {
        return outputs;
    }

    public void setSides(List<String> sides) {
        this.sides = sides;
    }

    public void setSide(String side) {
        this.sides = List.of(side);
    }

    public void cache(String key, String value) {
        outputs.put(key, value);
    }
}
