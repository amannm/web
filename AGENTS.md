---
name: Codex
description: Web browsing expert.
---

# Ultimate project objective
- An LLM-friendly CLI-based web browser.

# Philosophy and principles
- High visual density > progressive disclosure.
- Flat organization > hierarchical grouping.
- Minimal dependencies > ecosystem integration.
- Precision > convenience.
- Immutable > mutable.
- Composition > inheritance.
- Configuration > convention.
- Fail-fast > fail-safe.
- "You Aren't Gonna Need It" (YAGNI) > extensibility.
- Don't Repeat Yourself (DRY) > WET (duplication).
- Orchestration > choreography.
- Stateless > stateful.
- Static types > dynamic types.
- Concreteness > abstraction.
- Explicitness > implicitness.
- Readability > cleverness.
- Local reasoning > indirection.
- Strong contracts > loose coupling
- Simplification > backwards-compatibility.
- Quality > speed.
- Static analysis > runtime debugging.

# Coding and architectural style
- Make illegal states unrepresentable and valid operations obvious.
- Keep external dependencies furthest from the center of a codebase.
- Use self-documenting code <purpose>to increase source code information density</purpose>.
- Premature optimization is the root of all evil.
- Write strongly-typed, idiomatic, modern Java at language level 25.

# Specific language habits and preferences
- Try to keep all source code files over ~69 lines and under ~420 lines.
- Never hide exceptions or warnings.
- Never introduce unchecked casts.
- Never use `Optional<T>` for anything other than method return types.
- Never use `java.lang.reflect`.
- Never use `default` interface methods.
- Avoid declaring `null` or `Object` unless absolutely necessary.
- Prefer `sealed` and `final` over `non-sealed`.
- Prefer latest `switch` and `a instanceof T b` pattern matching capabilities over traditional null-checks and casting.
- (Leave/Follow) Markdown links (via `///`) within `.java` source files <purpose>to (Offer/Discover) additional context</purpose>.
- Clearly mark ALL workarounds, hacks, placeholders, mocks, incomplete areas with `// TODO:`.

# Environment features
- Unrestricted internet access enabled.
- `graalvm-jdk-25` toolchain.
- System-wide `gradle` is available -- DO NOT attempt to use `gradlew`.

# Reference material
- [Chromium Source Code](reference/chromium)
- [Chromium DevTools Protocol](reference/devtools-protocol)
- [Chrome DevTools Frontend Source Code](reference/devtools-frontend)
- [OpenAI API Reference](reference/openai)
- [Browser-Use Source Code](reference/browser-use)