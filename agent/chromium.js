import os from "node:os";
import path from "node:path";
import fs from "node:fs";
import fsPromises from "node:fs/promises";
import https from "node:https";
import http from "node:http";
import {spawn} from "node:child_process";
import {createHash} from "node:crypto";
import extract from "./extract.js";

const FALLBACK_VERSION = "143.0.7499.42"; // Used if remote lookup fails.
const CHROME_FOR_TESTING_BASE = "https://storage.googleapis.com/chrome-for-testing-public";
const KNOWN_DIGESTS = {
    "143.0.7499.42": {
        "mac-arm64": "94a4267ac337fe85075060440d1456756448f8a671562be56a5b29571495bb6c",
        "mac-x64": "664412fa6d087777330b6dc248fb62eb0f4391aa2a91fda8d1ff0894f28b8807",
        "linux64": "088b3c1c1668ae6f095661c5aa9dc5bab7acf2b8d9c4f3c5f97ae2dcab11ceec",
        "win64": "b347b9ac9f78e85f6143dcd8330be8e129b74e1242eb92ddcfa77ea364e9219d",
    },
};
let resolvedVersionCache = null;

function platformSpec() {
    const {platform, arch} = process;

    if (platform === "win32") {
        return {
            platformPath: "win64",
            archiveName: "chrome-win64.zip",
            unpackedDirName: "chrome-win64",
            executableRelativePath: path.join("chrome-win64", "chrome.exe"),
        };
    }

    if (platform === "linux") {
        if (arch === "arm64") {
            return {
                platformPath: "linux-arm64",
                archiveName: "chrome-linux-arm64.zip",
                unpackedDirName: "chrome-linux-arm64",
                executableRelativePath: path.join("chrome-linux-arm64", "chrome"),
            };
        }
        return {
            platformPath: "linux64",
            archiveName: "chrome-linux64.zip",
            unpackedDirName: "chrome-linux64",
            executableRelativePath: path.join("chrome-linux64", "chrome"),
        };
    }

    if (platform === "darwin") {
        if (arch === "arm64") {
            const executableRelativePath = path.join("chrome-mac-arm64", "Google Chrome for Testing.app", "Contents", "MacOS", "Google Chrome for Testing",
            );
            return {
                platformPath: "mac-arm64",
                archiveName: "chrome-mac-arm64.zip",
                unpackedDirName: "chrome-mac-arm64",
                executableRelativePath,
            };
        }

        const executableRelativePath = path.join("chrome-mac-x64", "Google Chrome for Testing.app", "Contents", "MacOS", "Google Chrome for Testing",);
        return {
            platformPath: "mac-x64",
            archiveName: "chrome-mac-x64.zip",
            unpackedDirName: "chrome-mac-x64",
            executableRelativePath,
        };
    }

    throw new Error(`Unsupported platform: ${platform} (${arch})`);
}

function resolveCacheDir(version) {
    if (process.env.CHROME_FOR_TESTING_CACHE) {
        return process.env.CHROME_FOR_TESTING_CACHE;
    }
    if (process.env.PLAYWRIGHT_CDP_CHROMIUM_CACHE) {
        return process.env.PLAYWRIGHT_CDP_CHROMIUM_CACHE;
    }
    return path.join(os.homedir(), ".cache", "chrome-for-testing", version);
}

function ensureDirSync(dir) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, {recursive: true});
    }
}

function downloadFile(url, destPath) {
    return new Promise((resolve, reject) => {
        const file = fs.createWriteStream(destPath);
        const request = https.get(url, (response) => {
            if (response.statusCode !== 200) {
                file.close(() => {
                    fs.unlink(destPath, () => {
                        // ignore unlink errors
                    });
                });
                reject(
                    new Error(
                        `Unexpected HTTP ${response.statusCode} while downloading ${url}`
                    )
                );
                response.resume();
                return;
            }
            response.pipe(file);
            file.on("finish", () => {
                file.close(() => resolve());
            });
        });
        request.on("error", (err) => {
            file.close(() => {
                fs.unlink(destPath, () => {
                    // ignore unlink errors
                });
            });
            reject(err);
        });
    });
}

function fetchRemoteDigest(url) {
    return new Promise((resolve) => {
        https.get(url, (res) => {
            if (res.statusCode !== 200) {
                res.resume();
                resolve(null);
                return;
            }
            let body = "";
            res.setEncoding("utf8");
            res.on("data", (chunk) => body += chunk);
            res.on("end", () => {
                const hex = body.trim().split(/\s+/)[0];
                if (/^[a-f0-9]{64}$/i.test(hex)) {
                    resolve(hex.toLowerCase());
                } else {
                    resolve(null);
                }
            });
        }).on("error", () => resolve(null));
    });
}

async function verifyChecksum(pathToFile, expectedHex) {
    const hash = createHash("sha256");
    const stream = fs.createReadStream(pathToFile);
    return new Promise((resolve, reject) => {
        stream.on("data", (chunk) => hash.update(chunk));
        stream.once("error", reject);
        stream.once("end", () => {
            const digest = hash.digest("hex");
            resolve(digest === expectedHex);
        });
    });
}

function lookupDigest(version, platformPath) {
    return KNOWN_DIGESTS[version]?.[platformPath];
}

async function fetchLatestVersion(channel = "Stable") {
    return new Promise((resolve, reject) => {
        https.get("https://googlechromelabs.github.io/chrome-for-testing/last-known-good-versions-with-downloads.json", (res) => {
            if (res.statusCode !== 200) {
                res.resume();
                reject(new Error(`HTTP ${res.statusCode} fetching last-known-good-versions`));
                return;
            }
            let body = "";
            res.setEncoding("utf8");
            res.on("data", (chunk) => body += chunk);
            res.on("end", () => {
                try {
                    const parsed = JSON.parse(body);
                    const version = parsed?.channels?.[channel]?.version;
                    if (typeof version === "string") {
                        resolve(version);
                        return;
                    }
                    reject(new Error("Missing version in last-known-good-versions JSON"));
                } catch (err) {
                    reject(err);
                }
            });
        }).on("error", reject);
    });
}

async function resolveChromeVersion() {
    if (resolvedVersionCache) {
        return resolvedVersionCache;
    }
    if (process.env.CHROME_FOR_TESTING_VERSION) {
        resolvedVersionCache = process.env.CHROME_FOR_TESTING_VERSION;
        return resolvedVersionCache;
    }
    try {
        resolvedVersionCache = await fetchLatestVersion("Stable");
        return resolvedVersionCache;
    } catch {
        resolvedVersionCache = FALLBACK_VERSION;
        return resolvedVersionCache;
    }
}

async function ensureChromiumDownloaded() {
    const version = await resolveChromeVersion();
    const {archiveName, executableRelativePath, platformPath} = platformSpec();
    const cacheDir = resolveCacheDir(version);
    const archivePath = path.join(cacheDir, archiveName);
    const executablePath = path.join(cacheDir, executableRelativePath);
    try {
        await fsPromises.access(executablePath, fs.constants.X_OK);
        return executablePath;
    } catch {
        // fall through and download
    }

    ensureDirSync(cacheDir);
    const downloadUrl = `${CHROME_FOR_TESTING_BASE}/${version}/${platformPath}/${archiveName}`;
    try {
        await fsPromises.access(archivePath, fs.constants.R_OK);
    } catch {
        await downloadFile(downloadUrl, archivePath);
    }
    const expected = lookupDigest(version, platformPath) ?? await fetchRemoteDigest(`${CHROME_FOR_TESTING_BASE}/${version}/${platformPath}/${archiveName}.sha256`);
    if (expected) {
        const ok = await verifyChecksum(archivePath, expected);
        if (!ok) {
            throw new Error(`Checksum verification failed for ${archiveName}`);
        }
    }
    await extract(archivePath, {dir: cacheDir});
    await fsPromises.access(executablePath, fs.constants.X_OK);
    return executablePath;
}

const CDP_READY_TIMEOUT_MS = Number(process.env.CDP_READY_TIMEOUT_MS ?? 45_000);

async function waitForCdpEndpoint(port, {attempts = Math.ceil(CDP_READY_TIMEOUT_MS / 200), delayMs = 200} = {}) {
    for (let i = 0; i < attempts; i += 1) {
        const ok = await new Promise((resolve) => {
            const req = http.get({host: "127.0.0.1", port, path: "/json/version", timeout: 1500}, (res) => {
                res.resume();
                resolve(res.statusCode === 200);
            });
            req.on("error", () => resolve(false));
            req.on("timeout", () => {
                req.destroy();
                resolve(false);
            });
        });
        if (ok) return;
        await new Promise((r) => setTimeout(r, delayMs));
    }
    throw new Error(`Timed out waiting for Chrome DevTools on port ${port} after ${attempts * delayMs}ms`);
}

class BrowserProcess {
    constructor(child) {
        this._child = child;
    }

    async close() {
        const child = this._child;
        if (!child) return;
        if (child.exitCode !== null) return;
        return new Promise((resolve) => {
            const timeout = setTimeout(() => {
                try {
                    child.kill("SIGKILL");
                } catch {
                    // ignore on platforms that do not support SIGKILL
                }
            }, 5000);
            child.once("exit", () => {
                clearTimeout(timeout);
                resolve();
            });
            try {
                child.kill("SIGTERM");
            } catch {
                // ignore
                resolve();
            }
        });
    }
}

async function launch({headless = true, args = []} = {}) {
    const executablePath = await ensureChromiumDownloaded();
    const finalArgs = [...args];
    if (headless && !finalArgs.some((arg) => arg.startsWith("--headless"))) {
        finalArgs.push("--headless=new");
    } else {
        finalArgs.push("--disable-infobars");
    }
    const suppressNoise = process.env.CHROME_SUPPRESS_NOISE !== "0";
    if (suppressNoise) {
        finalArgs.push(
            "--disable-features=PushMessaging",
            "--disable-background-networking",
            "--disable-sync",
            "--disable-logging",
            "--log-level=3"
        );
    }
    const portFlag = finalArgs.find((flag) => flag.startsWith("--remote-debugging-port="));
    const port = portFlag ? Number(portFlag.split("=")[1]) : undefined;
    return new Promise((resolve, reject) => {
        const child = spawn(executablePath, finalArgs, {
            stdio: "inherit",
        });
        const browser = new BrowserProcess(child);
        let settled = false;
        const abort = (err) => {
            if (settled) return;
            settled = true;
            // Ensure the child process is torn down before propagating the error.
            browser.close().catch(() => {
            }).finally(() => reject(err));
        };
        child.once("error", (err) => {
            abort(err);
        });
        child.once("spawn", async () => {
            if (settled) return;
            try {
                if (port) {
                    await waitForCdpEndpoint(port);
                }
                settled = true;
                resolve(browser);
            } catch (err) {
                abort(err);
            }
        });
    });
}

export const chromium = {
    launch,
};
