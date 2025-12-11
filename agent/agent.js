#!/usr/bin/env node

import Anthropic from "@anthropic-ai/sdk";
import {connectToNewPage, launchChromiumWithCdp} from "./cdp.js";

const MAX_TOOL_PAYLOAD_CHARS = 8_000;
const MODEL = "claude-opus-4-5-20251101";
const MAX_MODEL_TOKENS = 1_024;
const CDP_PORT = 9_222;
const CDP_HEADLESS = false;
const args = process.argv.slice(2);
const debugFlagIndex = args.indexOf("--debug");
const TRACE = debugFlagIndex >= 0;
if (debugFlagIndex >= 0) {
    args.splice(debugFlagIndex, 1);
}

const color = {
    blue: (s) => `\u001b[34m${s}\u001b[0m`,
    green: (s) => `\u001b[32m${s}\u001b[0m`,
    red: (s) => `\u001b[31m${s}\u001b[0m`,
    grey: (s) => `\u001b[90m${s}\u001b[0m`,
    none: (s) => s,
};

const colorize = (fn) => TRACE ? fn : color.none;
const logSend = (label, payload) => {
    if (TRACE) console.log(colorize(color.blue)(`[CDP SEND] ${label}: ${payload}`));
};
const logRecv = (label, payload, ok = true) => {
    if (TRACE) console.log(colorize(ok ? color.green : color.red)(`[CDP RECV] ${label}: ${payload}`));
};
const logThought = (text) => {
    if (TRACE && text) console.log(colorize(color.grey)(`[THINK] ${text}`));
};

const apiKey = process.env.ANTHROPIC_API_KEY;
if (!apiKey) {
    console.error("Missing ANTHROPIC_API_KEY environment variable. Please export it and retry.");
    process.exit(1);
}
const anthropic = new Anthropic({apiKey});
const tools = [{
    name: "cdp_command",
    description: "Send a single Chrome DevTools Protocol command to the attached tab.",
    input_schema: {
        type: "object",
        properties: {
            domain: {type: "string", description: "CDP domain name, e.g. 'Page' or 'Runtime'."},
            method: {type: "string", description: "Method in the domain, e.g. 'navigate' or 'evaluate'."},
            params: {type: "object", description: "Parameters object to pass to the CDP method.", default: {}}
        },
        required: ["domain", "method"],
        additionalProperties: false
    }
}];

function toMessageParamContent(blocks) {
    return blocks.map((block) => {
        if (block.type === "tool_use") {
            return {
                type: "tool_use",
                id: block.id,
                name: block.name,
                input: block.input,
            };
        }
        if (block.type === "text") {
            return {type: "text", text: block.text};
        }
        return block;
    });
}

async function connectChrome() {
    const port = CDP_PORT;
    try {
        const client = await connectToNewPage(port, {maxAttempts: 10, delayMs: 100});
        const {Page, Runtime} = client;
        await Promise.all([Page.enable(), Runtime.enable()]);
        return {
            client,
            browser: null,
            async close() {
                try {
                    await client.close();
                } catch (e) {
                    console.warn("Failed to close existing Chrome client", e);
                }
            }
        };
    } catch (reuseError) {
        console.info(`No existing Chrome DevTools session detected on port ${port}; launching new Chromium.`);
        const {browser} = await launchChromiumWithCdp({port, headless: CDP_HEADLESS});
        const client = await connectToNewPage(port);
        const {Page, Runtime} = client;
        await Promise.all([Page.enable(), Runtime.enable()]);
        return {
            client,
            browser,
            async close() {
                try {
                    if (client.Browser) {
                        await client.Browser.close();
                        return;
                    }
                } catch (e) {
                    // Fallback to process-level close below.
                }
                try {
                    await client.close();
                } catch (e) {
                    console.warn("Failed to close Chrome CDP client", e);
                }
                try {
                    await browser?.close();
                } catch (e) {
                    console.warn("Failed to close Chrome process", e);
                }
            }
        };
    }
}

async function runCdpCommand(client, args) {
    const {domain, method, params = {}} = args;
    const methodName = method?.includes(".") ? method : `${domain}.${method}`;
    logSend(methodName, JSON.stringify(params));
    try {
        const result = await client.call(methodName, params);
        logRecv(methodName, JSON.stringify(result), true);
        return result;
    } catch (err) {
        logRecv(methodName, String(err), false);
        return {error: String(err)};
    }
}

async function runAgent(rawUserText) {
    const userText = rawUserText?.trim() || "Open https://example.com, wait for it to load, grab document.title and tell me what it is.";
    const chrome = await connectChrome();
    const system = "You are connected to Chrome via its DevTools Protocol. Use the cdp_command tool to surf the web. Stay token-efficient.";
    const messages = [{role: "user", content: [{type: "text", text: userText}]}];
    try {
        for (let round = 0; ; round += 1) {
            const result = await anthropic.messages.create({
                model: MODEL,
                max_tokens: MAX_MODEL_TOKENS,
                system,
                messages,
                tools,
                tool_choice: {type: "auto"},
            });
            const toolUses = result.content
                .filter((b) => b.type === "tool_use" && b.name === "cdp_command");
            const texts = result.content
                .filter((b) => b.type === "text")
                .map((b) => b.text)
                .join("");
            if (toolUses.length === 0) {
                if (texts) {
                    logThought(texts);
                    console.log(texts);
                }
                return;
            }
            messages.push({role: "assistant", content: toMessageParamContent(result.content)});
            for (const use of toolUses) {
                const output = await runCdpCommand(chrome.client, use.input);
                const serialized = JSON.stringify(output);
                const trimmed = serialized.length > MAX_TOOL_PAYLOAD_CHARS
                    ? `${serialized.slice(0, MAX_TOOL_PAYLOAD_CHARS)}… (truncated)`
                    : serialized;
                if (TRACE) {
                    logRecv("cdp_command result", trimmed, true);
                }
                messages.push({
                    role: "user",
                    content: [{type: "tool_result", tool_use_id: use.id, content: trimmed}],
                });
            }
            if (texts) {
                logThought(texts);
                console.log(texts);
            }
        }
    } finally {
        try {
            await chrome.close();
        } catch (e) {
            console.warn("Failed to close Chrome cleanly", e);
        }
    }
}

const userText = args.join(" ");
runAgent(userText).catch(err => {
    console.error(err);
    process.exit(1);
});
