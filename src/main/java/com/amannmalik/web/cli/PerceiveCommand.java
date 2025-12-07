package com.amannmalik.web.cli;

import com.amannmalik.web.perception.PerceptionProfileProjector.PerceptionProfile;
import com.amannmalik.web.perception.PerceptionScope;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.net.URI;
import java.util.Locale;

@CommandLine.Command(
    name = "perceive",
    description = "Capture a perception snapshot for a URL and project it for the requested intent.",
    mixinStandardHelpOptions = true
)
public final class PerceiveCommand implements java.util.concurrent.Callable<Integer> {

    private final ChromiumPerceptionExecutor executor = new ChromiumPerceptionExecutor();

    @CommandLine.Spec
    private CommandSpec spec;

    @CommandLine.Parameters(index = "0", paramLabel = "URL", description = "HTTP(S) address to browse")
    private URI target;

    @CommandLine.Option(
        names = {"-p", "--profile"},
        converter = ProfileConverter.class,
        defaultValue = "multimodal",
        description = "LLM-oriented profile. Valid values: ${COMPLETION-CANDIDATES}.")
    private PerceptionProfile profile = PerceptionProfile.MULTIMODAL;

    @CommandLine.Option(
        names = {"-s", "--scope"},
        converter = ScopeConverter.class,
        defaultValue = "full-page",
        description = "Capture scope. Valid values: ${COMPLETION-CANDIDATES}.")
    private PerceptionScope scope = PerceptionScope.FULL_PAGE;

    public PerceiveCommand() {
    }

    @Override
    public Integer call() {
        var validatedTarget = validateTarget(target);
        var projection = executor.capture(validatedTarget, profile, scope);
        var out = spec.commandLine().getOut();
        out.println(projection.toString());
        return 0;
    }

    private URI validateTarget(URI candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("target URL must be provided");
        }
        var scheme = candidate.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("target URL must use http or https");
        }
        return candidate;
    }

    static final class ScopeConverter implements CommandLine.ITypeConverter<PerceptionScope> {
        @Override
        public PerceptionScope convert(String value) {
            try {
                return PerceptionScope.fromCliName(value);
            } catch (IllegalArgumentException ex) {
                var normalized = value == null ? "null" : value.trim().toLowerCase(Locale.ROOT);
                throw new CommandLine.TypeConversionException("Invalid scope '" + normalized + "'. Expected one of: "
                    + PerceptionScope.cliNameList());
            }
        }
    }

    static final class ProfileConverter implements CommandLine.ITypeConverter<PerceptionProfile> {
        @Override
        public PerceptionProfile convert(String value) {
            try {
                return PerceptionProfile.fromCliName(value);
            } catch (IllegalArgumentException ex) {
                var normalized = value == null ? "null" : value.trim().toLowerCase(Locale.ROOT);
                throw new CommandLine.TypeConversionException(
                    "Invalid profile '" + normalized + "'. Expected one of: text, visual, multimodal, debug");
            }
        }
    }
}
