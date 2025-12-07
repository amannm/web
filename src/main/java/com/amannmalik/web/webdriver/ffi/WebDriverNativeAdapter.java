package com.amannmalik.web.webdriver.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight adapter around the WebKit WebDriver native entrypoint using the Java FFM API.
 * /// https://www.w3.org/TR/webdriver/
 */
public final class WebDriverNativeAdapter {
    private static final FunctionDescriptor MAIN_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS
    );

    private static final String[] DEFAULT_SYMBOLS = { "WebDriverProcessMain", "main" };

    private final Path libraryPath;
    private final MethodHandle entrypointHandle;
    private final Arena libraryArena;
    private final List<String> resolvedSymbols;

    public WebDriverNativeAdapter(Path libraryPath) {
        this(libraryPath, List.of(DEFAULT_SYMBOLS));
    }

    public WebDriverNativeAdapter(Path libraryPath, List<String> symbolCandidates) {
        this.libraryPath = Objects.requireNonNull(libraryPath, "libraryPath");
        Objects.requireNonNull(symbolCandidates, "symbolCandidates");
        if (symbolCandidates.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol candidate is required.");
        }

        resolvedSymbols = List.copyOf(symbolCandidates);
        libraryArena = Arena.ofShared();
        var lookup = SymbolLookup.libraryLookup(libraryPath, libraryArena);
        var entrypointSymbol = resolveSymbol(lookup, resolvedSymbols);
        var linker = Linker.nativeLinker();
        entrypointHandle = linker.downcallHandle(entrypointSymbol, MAIN_DESCRIPTOR);
    }

    public int run(String... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        return run(List.of(arguments));
    }

    public int run(List<String> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(libraryArena); // keep the shared arena reachable for the lifetime of this adapter
        var argv = prependProgramName(arguments);
        try (var callArena = Arena.ofConfined()) {
            var argvSegment = callArena.allocate(ValueLayout.ADDRESS, argv.size());
            for (var index = 0; index < argv.size(); index++) {
                var cString = callArena.allocateFrom(argv.get(index), StandardCharsets.UTF_8);
                argvSegment.setAtIndex(ValueLayout.ADDRESS, index, cString);
            }
            return (int) entrypointHandle.invokeExact(argv.size(), argvSegment);
        } catch (Throwable throwable) {
            throw new WebDriverLaunchException(
                    "Unable to invoke WebDriver entrypoint in " + libraryPath + " using symbols " + resolvedSymbols,
                    throwable
            );
        }
    }

    private static MemorySegment resolveSymbol(SymbolLookup lookup, List<String> symbolCandidates) {
        for (var candidate : symbolCandidates) {
            var symbol = lookup.find(candidate);
            if (symbol.isPresent()) {
                return symbol.get();
            }
        }
        throw new WebDriverSymbolNotFoundException(symbolCandidates);
    }

    private static List<String> prependProgramName(List<String> arguments) {
        var argv = new ArrayList<String>(arguments.size() + 1);
        argv.add("webdriver");
        argv.addAll(arguments);
        return argv;
    }
}
