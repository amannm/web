#!/usr/bin/env node

import Anthropic from "@anthropic-ai/sdk";
import readline from "node:readline/promises";
import {stdin as input, stdout as output} from "node:process";
import {connectToNewPage, launchChromiumWithCdp} from "./cdp.js";
import sharp from "sharp";

const MAX_TOOL_PAYLOAD_CHARS = 8_000;
const MODEL = "claude-opus-4-5-20251101";
const MAX_MODEL_TOKENS = 1_024;
const CDP_PORT = 9_222;
const CDP_HEADLESS = false;
const MAX_IMAGE_BYTES = 5_000_000;
const MAX_IMAGE_DIM = 8_000;
const ALLOWED_IMAGE_FORMATS = new Set(["png", "jpeg", "jpg", "webp", "gif"]);
const CAN_PROMPT_USER = input.isTTY;
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

const toFullMethodName = (domain, method) => method?.includes(".") ? method : `${domain}.${method}`;
const isScreenshotOutput = (methodName, output) =>
    Boolean(methodName?.endsWith(".captureScreenshot") && output && typeof output.data === "string");

const toMessageContent = (blocks) => blocks.map((block) => {
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

async function normalizeImage(base64Data) {
    const input = Buffer.from(base64Data, "base64");
    const pipeline = sharp(input, {limitInputPixels: MAX_IMAGE_DIM * MAX_IMAGE_DIM});
    const metadata = await pipeline.metadata();
    const format = metadata.format?.toLowerCase();
    if (!format || !ALLOWED_IMAGE_FORMATS.has(format)) {
        throw new Error(`Unsupported screenshot format: ${metadata.format ?? "unknown"}`);
    }
    const width = metadata.width ?? MAX_IMAGE_DIM;
    const height = metadata.height ?? MAX_IMAGE_DIM;
    const scaleForDims = Math.min(1, MAX_IMAGE_DIM / width, MAX_IMAGE_DIM / height);
    const resized = scaleForDims < 1
        ? pipeline.resize({
            width: Math.floor(width * scaleForDims),
            height: Math.floor(height * scaleForDims),
            fit: "inside",
        })
        : pipeline;
    let {data: buffer, info} = await resized.toFormat(format).toBuffer({resolveWithObject: true});
    const shrinkUntilFits = async () => {
        let currentBuffer = buffer;
        let currentInfo = info;
        for (let attempts = 0; attempts < 5 && currentBuffer.length > MAX_IMAGE_BYTES; attempts += 1) {
            const factor = Math.sqrt(MAX_IMAGE_BYTES / currentBuffer.length);
            const nextWidth = Math.max(1, Math.floor((currentInfo.width ?? MAX_IMAGE_DIM) * factor));
            const nextHeight = Math.max(1, Math.floor((currentInfo.height ?? MAX_IMAGE_DIM) * factor));
            const next = await sharp(currentBuffer)
                .resize({width: nextWidth, height: nextHeight, fit: "inside"})
                .toFormat(format)
                .toBuffer({resolveWithObject: true});
            currentBuffer = next.data;
            currentInfo = next.info;
        }
        return {data: currentBuffer, info: currentInfo};
    };
    const shrunk = buffer.length > MAX_IMAGE_BYTES ? await shrinkUntilFits() : {data: buffer, info};
    const mediaType = `image/${format === "jpg" ? "jpeg" : format}`;
    return {base64: shrunk.data.toString("base64"), mediaType};
}

async function runAgent(rawUserText) {
    const userText = rawUserText?.trim() || "Open https://example.com, wait for it to load, grab document.title and tell me what it is.";
    const chrome = await connectChrome();
    const system = "You are connected to Chrome via its DevTools Protocol. Use the cdp_command tool to surf the web. Stay token-efficient.";
    const messages = [{role: "user", content: [{type: "text", text: userText}]}];
    const rl = CAN_PROMPT_USER ? readline.createInterface({input, output}) : null;
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
            const content = toMessageContent(result.content);
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
                messages.push({role: "assistant", content});
                const stopReason = result.stop_reason ?? "end_turn";
                const autoContinue = stopReason === "max_tokens" || stopReason === "pause_turn";
                if (autoContinue) {
                    continue;
                }
                if (rl) {
                    const followUp = (await rl.question(colorize(color.grey)("Reply (leave blank to finish): "))).trim();
                    if (followUp) {
                        messages.push({role: "user", content: [{type: "text", text: followUp}]});
                        continue;
                    }
                }
                return;
            }
            messages.push({role: "assistant", content});
            for (const use of toolUses) {
                const methodName = toFullMethodName(use.input.domain, use.input.method);
                const output = await runCdpCommand(chrome.client, use.input);
                const screenshot = isScreenshotOutput(methodName, output);
                const sanitized = screenshot ? {...output, data: "<base64 image/png omitted>"} : output;
                const serialized = JSON.stringify(sanitized);
                const trimmed = serialized.length > MAX_TOOL_PAYLOAD_CHARS
                    ? `${serialized.slice(0, MAX_TOOL_PAYLOAD_CHARS)}… (truncated)`
                    : serialized;
                if (TRACE) {
                    logRecv("cdp_command result", trimmed, true);
                }
                let toolResultContent = trimmed;
                if (screenshot) {
                    try {
                        const processed = await normalizeImage(output.data);
                        toolResultContent = [{type: "image", source: {type: "base64", media_type: processed.mediaType, data: processed.base64}}];
                    } catch (e) {
                        const message = e instanceof Error ? e.message : String(e);
                        toolResultContent = [{type: "text", text: `Failed to normalize screenshot: ${message}`}];
                    }
                }
                messages.push({
                    role: "user",
                    content: [{type: "tool_result", tool_use_id: use.id, content: toolResultContent}],
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
        try {
            rl?.close();
        } catch {
            // ignore readline close errors
        }
    }
}

const userText = args.join(" ");
runAgent(userText).catch(err => {
    console.error(err);
    process.exit(1);
});
