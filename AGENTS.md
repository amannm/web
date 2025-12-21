# Project objectives
- An LLM-friendly CLI-based web browser.

# Codebase style
- High density
- Flat organization
- Minimalistic
- Explicitly wired
- Internally consistent
- Artisanal

# Preferred approaches
- Prefer modern, idiomatic Java.
- Prefer restricting access to internals.
- Prefer approaches friendly with static analysis tools.
- Prefer keeping source code files **between ~69 and ~420 lines**.
- Prefer pattern matching and destructuring approaches.
- Prefer defining additional types to make invalid states unrepresentable.
- Prefer self-documenting code.
- Prefer `sealed`, `final` over `non-sealed`.
- Prefer `java.util.concurrent.atomic`, `java.util.concurrent.locks` over `volatile`, `synchronized`.
- Prefer `if (jsonObject.get("keyName") instanceof JsonString value) { return value.getString(); }` over `if (jsonObject.containsKey("keyName")) { return jsonObject.getString("keyName"); }`.
- Prefer using `Path`, `Instant`, `Duration` over primitive representations.
- Prefer using Java's standard library to minimize dependencies and reduce maintenance overhead.
- Prefer leaving a `// TODO:` on all workarounds, hacks, placeholders, mocks, incomplete areas.
- Prefer static dispatch over dynamic dispatch.
- Prefer stateless components.
- Prefer using generics where it makes sense.
- Prefer making illegal states unrepresentable and valid operations obvious.
- Prefer keeping external dependencies furthest from the center of a codebase.
- Prefer abiding by "You Aren't Gonna Need It" (YAGNI).

# Avoided approaches
- Avoid internal abstractions that add no value.
- Avoid over-parameterization from premature generalization.
- Avoid imports from too many different packages.
- Avoid helper methods used in only a single spot.
- Avoid defensive complexity for impossible situations.
- Avoid comments unless necessary.
- Avoid framework features reliant on annotation processing.
- Avoid runtime reflection.
- Avoid leaving around unused methods, constructors, parameters.
- Avoid insidious, damaging knots of unnecessary complexity.
- Avoid features removed or still in preview as of **language level 25**.
- Avoid `Optional` for anything other than method returns.
- Avoid `.orElse(null)` terminating an `Optional` chain.
- Avoid making unchecked casts.
- Avoid `default` interface methods.
- Avoid declaring `Object` or `null`.
- Avoid creating "Multi-File Source-Code Programs" (JEP 458).
- Avoid trying to "fix" timed-out tests by blindly increasing their timeout.
- Avoid `@FunctionalInterface` interfaces.
- Avoid single line methods.
- Avoid non-private fields.
- Avoid premature optimization.
- Avoid hiding exceptions or warnings.
- Avoid adding speculative code.

# Environment features
- Unrestricted internet access.
- `graalvm-jdk-25` Java toolchain.
- `gradle` (not `gradlew`).

# Reference material
- [Chromium Source Code](reference/chromium)
- [Chrome DevTools Frontend Source Code](reference/devtools-frontend)
- [Chromium DevTools Protocol](reference/devtools-protocol)
- [OpenAI API Reference](reference/openai)
- [Server-Sent Events (SSE) Specification](reference/server-sent-events.html)