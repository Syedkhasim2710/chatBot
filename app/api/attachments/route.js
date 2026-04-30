import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { NextResponse } from "next/server";

export const runtime = "nodejs";

const MAX_FILES = 5;
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_TEXT_PREVIEW = 2000;
const UPLOAD_DIR = path.join(process.cwd(), ".uploads");

const TEXT_EXTENSIONS = new Set([
  ".csv",
  ".java",
  ".js",
  ".json",
  ".jsx",
  ".log",
  ".md",
  ".py",
  ".sql",
  ".text",
  ".ts",
  ".tsx",
  ".txt",
  ".xml",
  ".yaml",
  ".yml",
]);

const TEXT_MIME_TYPES = new Set([
  "application/json",
  "application/xml",
  "application/yaml",
  "image/svg+xml",
  "text/csv",
  "text/html",
  "text/javascript",
  "text/markdown",
  "text/plain",
  "text/xml",
]);

function sanitizeFilename(filename) {
  return filename.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function isTextAttachment(file) {
  const extension = path.extname(file.name || "").toLowerCase();
  return file.type.startsWith("text/") || TEXT_MIME_TYPES.has(file.type) || TEXT_EXTENSIONS.has(extension);
}

function normalizePreviewText(buffer) {
  return new TextDecoder("utf-8", { fatal: false })
    .decode(buffer)
    .replace(/\u0000/g, "")
    .trim()
    .slice(0, MAX_TEXT_PREVIEW);
}

function errorResponse(message, status) {
  return NextResponse.json(
    {
      success: false,
      message,
    },
    { status }
  );
}

export async function POST(request) {
  try {
    const formData = await request.formData();
    const files = formData.getAll("files").filter((entry) => entry instanceof File);

    if (!files.length) {
      return errorResponse("Select at least one file to upload.", 400);
    }

    if (files.length > MAX_FILES) {
      return errorResponse(`You can upload up to ${MAX_FILES} files at a time.`, 400);
    }

    await mkdir(UPLOAD_DIR, { recursive: true });

    const uploadedFiles = await Promise.all(
      files.map(async (file) => {
        if (file.size > MAX_FILE_SIZE) {
          throw new Error(`${file.name} exceeds the 5 MB upload limit.`);
        }

        const storedName = `${Date.now()}-${sanitizeFilename(file.name)}`;
        const outputPath = path.join(UPLOAD_DIR, storedName);
        const arrayBuffer = await file.arrayBuffer();
        const buffer = Buffer.from(arrayBuffer);

        await writeFile(outputPath, buffer);

        const previewText = isTextAttachment(file) ? normalizePreviewText(buffer) : "";

        return {
          id: crypto.randomUUID(),
          name: file.name,
          size: file.size,
          type: file.type || "application/octet-stream",
          storedName,
          uploadedAt: new Date().toISOString(),
          textExtracted: Boolean(previewText),
          excerpt: previewText,
        };
      })
    );

    return NextResponse.json({
      success: true,
      data: uploadedFiles,
      message: `${uploadedFiles.length} attachment${uploadedFiles.length === 1 ? "" : "s"} uploaded.`,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Attachment upload failed.";
    const status = message.includes("5 MB") ? 400 : 500;
    return errorResponse(message, status);
  }
}
