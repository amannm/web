package com.amannmalik.web;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "web",
        mixinStandardHelpOptions = true,
        description = "LLM-friendly CLI browser driver that proxies CDP to OpenAI Responses.",
        versionProvider = Entrypoint.VersionProvider.class)
public final class Entrypoint implements Runnable {

    @SuppressWarnings("unused")
    @Option(names = {"-p", "--cdp-port"},
            required = true,
            description = "Local Chrome DevTools Protocol port (e.g., 9222).")
    int cdpPort;

    @SuppressWarnings("unused")
    @Parameters(paramLabel = "PROMPT",
            arity = "1..*",
            description = "Instruction to run via the browsing agent.")
    String[] promptParts;

    private Entrypoint() {
    }

    @Override
    public void run() {
        var prompt = String.join(" ", promptParts).trim();
        var exit = Agent.run(cdpPort, prompt);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    static void main(String[] args) {
        var code = new CommandLine(new Entrypoint())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[]{"web browser agent (module web.main)"};
        }
    }
}
