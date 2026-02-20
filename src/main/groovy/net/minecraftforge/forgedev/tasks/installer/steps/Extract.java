package net.minecraftforge.forgedev.tasks.installer.steps;

import net.minecraftforge.forgedev.tasks.installer.Tool;

import javax.inject.Inject;
import java.io.Serial;
import java.util.List;

public class Extract extends Step {
    @Serial
    private static final long serialVersionUID = 1L;

    @Inject
    public Extract(Tool tool) {
        super(tool);
        args.addAll(List.of(
            "--task", "EXTRACT_FILES"
        ));
    }

    public void archive(String archive) {
        args.add("--archive");
        args.add(archive);
    }

    public void extract(String from, String to) {
        args.addAll(List.of(
            "--from", from,
            "--to", to
        ));
    }

    public void optional(String from, String to) {
        args.addAll(List.of(
            "--from", from,
            "--to", to,
            "--optional", to
        ));
    }

    public void executable(String executable) {
        args.add("--exec");
        args.add(executable);
    }
}
