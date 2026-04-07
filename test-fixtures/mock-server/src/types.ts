export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface RestFixture {
  /** HTTP method this fixture matches. */
  method: HttpMethod;
  /** URL path this fixture matches (exact match, no wildcards). */
  path: string;
  /** HTTP status code to return. */
  status: number;
  /** Response Content-Type header. */
  contentType: string;
  /** Response body bytes. */
  body: Uint8Array;
}

export interface SseScriptChunk {
  /** Milliseconds to wait before sending this chunk. */
  delayMs: number;
  /** Raw SSE chunk text (must end with `\n\n` for the client to flush). */
  chunk: string;
}

export interface WsScriptFrame {
  /** Milliseconds to wait before sending this frame. */
  delayMs: number;
  /** WebSocket opcode: text, binary, or close. */
  opcode: 'text' | 'binary' | 'close';
  /** Frame payload. For binary, base64-encoded. For close, ignored. */
  payload: string;
  /** Optional close code (only used when opcode === 'close'). */
  closeCode?: number;
}

export interface FixtureSet {
  /** Map keyed by `${method} ${path}` → fixture. */
  rest: Map<string, RestFixture>;
  /** Map keyed by SSE script name (filename without extension) → absolute file path. */
  sse: Map<string, string>;
  /** Map keyed by WS script name (filename without extension) → absolute file path. */
  ws: Map<string, string>;
}
