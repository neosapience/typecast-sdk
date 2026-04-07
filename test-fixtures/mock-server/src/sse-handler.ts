import { readFile } from 'node:fs/promises';
import type { ServerResponse } from 'node:http';
import type { SseScriptChunk } from './types.ts';

export function parseSseScript(text: string): SseScriptChunk[] {
  const rawChunks = text.split(/\n---\n/);
  const chunks: SseScriptChunk[] = [];

  for (const raw of rawChunks) {
    const lines = raw.split('\n');
    let delayMs = 0;
    let startIdx = 0;

    if (lines[0]?.startsWith('# delay-ms:')) {
      const match = lines[0].match(/^# delay-ms:\s*(\d+)\s*$/);
      if (match) {
        delayMs = Number(match[1]);
        startIdx = 1;
      }
    }

    const chunkText = lines.slice(startIdx).join('\n');
    if (chunkText.trim().length === 0) continue;
    chunks.push({ delayMs, chunk: ensureTrailingBlankLine(chunkText) });
  }

  return chunks;
}

function ensureTrailingBlankLine(text: string): string {
  if (text.endsWith('\n\n')) return text;
  if (text.endsWith('\n')) return text + '\n';
  return text + '\n\n';
}

export async function streamSse(
  res: ServerResponse,
  scriptPath: string,
): Promise<void> {
  // streamSse is invoked fire-and-forget from the HTTP route, so any thrown
  // error here would surface as an unhandled rejection. Contain failures
  // locally and always close the response cleanly.
  try {
    const text = await readFile(scriptPath, 'utf8');
    const chunks = parseSseScript(text);

    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    });

    for (const chunk of chunks) {
      if (chunk.delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, chunk.delayMs));
      }
      if (res.writableEnded) return;
      res.write(chunk.chunk);
    }

    res.end();
  } catch (err) {
    if (!res.headersSent) {
      res.writeHead(500, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'sse stream failed', message: String(err) }));
      return;
    }
    if (!res.writableEnded) {
      res.end();
    }
  }
}
