# Verification

1) Install deps: `pnpm install`
2) Set your Anthropic key (required): `export ANTHROPIC_API_KEY=sk-...`
3) (Optional) Pick a CDP port / headless mode: `export CDP_PORT=9222` and `export CDP_HEADLESS=true|false` (defaults: 9222, headful)
4) Run the agent: `pnpm start -- "Open https://example.com and report document.title"`

Notes

- If a Chrome with remote debugging is already running on `CDP_PORT` (default 9222), the agent reuses the first target instead of creating a new one.
- Use model ID `claude-opus-4-5-20251101` for best performance.
