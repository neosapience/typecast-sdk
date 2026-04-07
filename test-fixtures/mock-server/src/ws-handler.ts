import { readFile } from 'node:fs/promises';
import type { WebSocket } from 'ws';
import type { WsScriptFrame } from './types.ts';

export function parseWsScript(text: string): WsScriptFrame[] {
  const frames: WsScriptFrame[] = [];
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (trimmed.length === 0) continue;
    frames.push(JSON.parse(trimmed) as WsScriptFrame);
  }
  return frames;
}

export async function streamWs(socket: WebSocket, scriptPath: string): Promise<void> {
  const text = await readFile(scriptPath, 'utf8');
  const frames = parseWsScript(text);

  for (const frame of frames) {
    if (frame.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, frame.delayMs));
    }
    if (socket.readyState !== socket.OPEN) return;

    if (frame.opcode === 'text') {
      socket.send(frame.payload);
    } else if (frame.opcode === 'binary') {
      socket.send(Buffer.from(frame.payload, 'base64'));
    } else {
      socket.close(frame.closeCode ?? 1000);
      return;
    }
  }
}
