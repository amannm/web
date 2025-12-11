import {chromium} from "./chromium.js";
import {WebSocket} from "ws";

const CALL_TIMEOUT_MS = Number(process.env.CDP_CALL_TIMEOUT_MS ?? 10_000);

export async function launchChromiumWithCdp({port = 9222, headless = false} = {}) {
    const browser = await chromium.launch({headless, args: [`--remote-debugging-port=${port}`]});
    return {browser, port};
}

function extractWsUrl(candidate) {
    if (candidate && typeof candidate === "object" && "webSocketDebuggerUrl" in candidate) {
        const value = candidate.webSocketDebuggerUrl;
        if (typeof value === "string") {
            return value;
        }
    }
    return undefined;
}

function createCdpClient(webSocketUrl) {
    const socket = new WebSocket(webSocketUrl);
    let nextId = 0;
    const pending = new Map();
    const listeners = new Map();
    const ready = new Promise((resolve, reject) => {
        socket.addEventListener("open", () => resolve(), {once: true});
        socket.addEventListener("error", (err) => reject(err), {once: true});
    });
    socket.addEventListener("message", (event) => {
        try {
            const message = JSON.parse(event.data);
            if (!message) {
                return;
            }
            if (typeof message.id !== "number") {
                const callbacks = listeners.get(message.method);
                if (callbacks) {
                    const errors = [];
                    callbacks.forEach((cb) => {
                        try {
                            cb(message.params ?? {});
                        } catch (cbError) {
                            errors.push(cbError);
                        }
                    });
                    if (errors.length > 0) {
                        const aggregate = new AggregateError(errors, `CDP listener(s) failed for ${message.method}`);
                        console.warn(aggregate);
                        rejectAll(aggregate);
                        try {
                            socket.close();
                        } catch {
                            // ignore close errors
                        }
                        return;
                    }
                }
                return;
            }
            const entry = pending.get(message.id);
            if (!entry) {
                return;
            }
            pending.delete(message.id);
            const {resolve, reject, timer} = entry;
            clearTimeout(timer);
            if (message.error) {
                reject(new Error(message.error.message ?? JSON.stringify(message.error)));
            } else {
                resolve(message.result);
            }
        } catch (error) {
            // Ignore malformed messages; CDP should always send JSON
        }
    });
    const rejectAll = (err) => {
        pending.forEach(({reject, timer}) => {
            clearTimeout(timer);
            reject(err instanceof Error ? err : new Error(String(err)));
        });
        pending.clear();
    };
    socket.addEventListener("error", rejectAll);
    socket.addEventListener("close", () => rejectAll(new Error("CDP socket closed")));
    function call(method, params = {}) {
        if (socket.readyState === WebSocket.CLOSING || socket.readyState === WebSocket.CLOSED) {
            return Promise.reject(new Error(`CDP socket is not open for ${method}`));
        }
        return ready.then(() => new Promise((resolve, reject) => {
            const id = ++nextId;
            const timer = setTimeout(() => {
                pending.delete(id);
                reject(new Error(`CDP call timed out after ${CALL_TIMEOUT_MS}ms: ${method}`));
            }, CALL_TIMEOUT_MS);
            pending.set(id, {resolve, reject, timer});
            socket.send(JSON.stringify({id, method, params}));
        }));
    }
    const domain = (name) => new Proxy({}, {
        get(_, prop) {
            return (params) => call(`${name}.${String(prop)}`, params);
        }
    });
    const on = (eventName, handler) => {
        const list = listeners.get(eventName) ?? [];
        list.push(handler);
        listeners.set(eventName, list);
        return () => listeners.set(eventName, list.filter((h) => h !== handler));
    };
    const close = () => new Promise((resolve) => {
        const done = () => resolve();
        socket.addEventListener("close", done, {once: true});
        try {
            socket.close();
        } catch (err) {
            rejectAll(err);
            resolve();
        }
    });
    return {
        on,
        call: call,
        Page: domain("Page"),
        Runtime: domain("Runtime"),
        Browser: domain("Browser"),
        Target: domain("Target"),
        close,
    };
}

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`HTTP ${response.status} fetching ${url}`);
    }
    return await response.json();
}

export async function connectToNewPage(port = 9222, {maxAttempts = 100, delayMs = 100} = {}) {
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
        try {
            try {
                const created = await fetchJson(`http://127.0.0.1:${port}/json/new`);
                const newWsUrl = extractWsUrl(created);
                if (newWsUrl) {
                    return createCdpClient(newWsUrl);
                }
                return Promise.reject(new Error("CDP /json/new did not return a webSocketDebuggerUrl"));
            } catch (error) {
                if (error instanceof Error && error.message.startsWith("HTTP 405")) {
                    const targets = await fetchJson(`http://127.0.0.1:${port}/json/list`);
                    const firstTarget = Array.isArray(targets) ? targets.find((t) => Boolean(extractWsUrl(t))) : undefined;
                    const firstWsUrl = extractWsUrl(firstTarget);
                    if (firstWsUrl) {
                        return createCdpClient(firstWsUrl);
                    }
                }
                return Promise.reject(error);
            }
        } catch (error) {
            if (attempt === maxAttempts) {
                throw error;
            }
            await new Promise(resolve => setTimeout(resolve, delayMs));
        }
    }
    throw new Error("unreachable");
}

if (import.meta.url === `file://${process.argv[1]}`) {
    const portEnv = process.env.CDP_PORT;
    const headlessEnv = process.env.CDP_HEADLESS;
    const port = portEnv ? Number(portEnv) : 9222;
    const headless = headlessEnv === "1" || headlessEnv === "true";
    launchChromiumWithCdp({port, headless})
        .then(async ({browser, port}) => {
            const client = await connectToNewPage(port);
            await client.Page.enable();
            console.log(`chromium listening for CDP on ${port}`);
            const shutdown = () => {
                Promise.allSettled([client.close(), browser.close()])
                    .finally(() => process.exit(0));
            };
            process.on("SIGINT", shutdown);
            process.on("SIGTERM", shutdown);
        })
        .catch(err => {
            console.error(err);
            process.exit(1);
        });
}
