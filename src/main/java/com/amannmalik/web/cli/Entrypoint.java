package com.amannmalik.web.cli;

import picocli.CommandLine;

import java.io.PrintWriter;

@CommandLine.Command(
        name = "web",
        description = "A web browser for agents.",
        mixinStandardHelpOptions = true,
        versionProvider = Entrypoint.ManifestVersionProvider.class
)
public final class Entrypoint implements Runnable {
    public Entrypoint() {
    }

    static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    public static CommandLine commandLine() {
        var entrypoint = new Entrypoint();
        var commandLine = new CommandLine(entrypoint);
        commandLine.setCaseInsensitiveEnumValuesAllowed(false);
        commandLine.addSubcommand(new PerceiveCommand());
        return commandLine;
    }

    @Override
    public void run() {
        var cli = commandLine();
        cli.usage(new PrintWriter(System.out, true));
    }

    public static final class ManifestVersionProvider implements CommandLine.IVersionProvider {
        public ManifestVersionProvider() {
        }

        @Override
        public String[] getVersion() {
            var pkg = Entrypoint.class.getPackage();
            var implementationVersion = pkg != null ? pkg.getImplementationVersion() : null;
            var version = implementationVersion != null ? implementationVersion : "web (development build)";
            return new String[]{version};
        }
    }
}
