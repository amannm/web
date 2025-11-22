package com.amannmalik.web.cli;

import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

public final class Entrypoint {
    private Entrypoint() {
    }

    static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    public static CommandLine commandLine() {
        var commandLine = new CommandLine(new RootCommand());
        return commandLine;
    }

    @Command(
            name = "web",
            description = "Web CLI",
            mixinStandardHelpOptions = true,
            versionProvider = ManifestVersionProvider.class)
    static final class RootCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        RootCommand() {
        }

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
    }

    public static final class ManifestVersionProvider implements IVersionProvider {
        public ManifestVersionProvider() {
        }

        @Override
        public String[] getVersion() {
            var version = Entrypoint.class.getPackage().getImplementationVersion();
            if (version == null || version.isBlank()) {
                version = "development";
            }
            return new String[]{"web " + version};
        }
    }
}
