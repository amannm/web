import fsPromises from "node:fs/promises";
import {spawn} from "node:child_process";

function run(command, args, options = {}) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, {
            stdio: "inherit", ...options,
        });
        child.once("error", (err) => {
            reject(err);
        });
        child.once("exit", (code) => {
            if (code === 0) {
                resolve();
            } else {
                reject(
                    new Error(`Command failed: ${command} ${args.join(" ")} (exit code ${code})`)
                );
            }
        });
    });
}

async function extractZipWithUnzip(archivePath, dir) {
    await run("unzip", ["-q", archivePath, "-d", dir]);
}

async function extractZipWithTar(archivePath, dir) {
    await run("tar", ["-xf", archivePath, "-C", dir]);
}

function escapeForPwshLiteral(str) {
    return str.replace(/'/g, "''");
}

async function extractZipWithPowerShell(archivePath, dir) {
    const literalArchive = escapeForPwshLiteral(archivePath);
    const literalDir = escapeForPwshLiteral(dir);
    const command = `Expand-Archive -LiteralPath '${literalArchive}' -DestinationPath '${literalDir}' -Force`;
    await run("powershell.exe", ["-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command]);
}

export default async function extract(archivePath, options = {}) {
    const {dir} = options;
    if (!archivePath) {
        throw new Error("extract: archivePath is required");
    }
    if (!dir) {
        throw new Error("extract: 'dir' option is required");
    }
    await fsPromises.mkdir(dir, {recursive: true});
    const platform = process.platform;
    if (platform === "win32") {
        try {
            await extractZipWithPowerShell(archivePath, dir);
            return;
        } catch (error) {
            throw new Error(
                `Failed to extract archive on Windows using PowerShell: ${error.message}`
            );
        }
    }
    try {
        await extractZipWithUnzip(archivePath, dir);
    } catch (unzipError) {
        try {
            await extractZipWithTar(archivePath, dir);
        } catch (tarError) {
            const error = new Error("Failed to extract archive: neither 'unzip' nor 'tar' succeeded");
            error.cause = {unzipError, tarError};
            throw error;
        }
    }
}
