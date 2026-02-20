package net.minecraftforge.forgedev.tasks.installer.steps;

import net.minecraftforge.forgedev.tasks.installer.Tool;

import javax.inject.Inject;
import java.io.Serial;
import java.util.List;

public class ExtractBundle extends Step {
    @Serial
    private static final long serialVersionUID = 1L;

    @Inject
    public ExtractBundle(Tool tool) {
        super(tool);
        args.addAll(List.of(
            "--task", "BUNDLER_EXTRACT"
        ));
    }

    public void extract(String from, String to) {
        args.addAll(List.of(
            "--input", from,
            "--output", to
        ));
    }

    public void libraries() {
        libraries("{MINECRAFT_JAR}", "{ROOT}/libraries/");
    }

    public void libraries(String from, String to) {
        extract("{MINECRAFT_JAR}", "{ROOT}/libraries/");
        args.add("--libraries");
    }

    public void jar(String from, String to) {
        extract(from, to);
        args.add("--jar-only");
    }
}
