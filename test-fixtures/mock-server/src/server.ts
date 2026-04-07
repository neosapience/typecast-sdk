import { createServer as createHttpServer, IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { WebSocketServer } from 'ws';
import { loadFixtures } from './fixture-loader.ts';
import { matchRest } from './rest-handler.ts';
import { streamSse } from './sse-handler.ts';
import { streamWs } from './ws-handler.ts';
import type { FixtureSet, HttpMethod } from './types.ts';

export interface ServerOptions {
  port: number;
  fixturesDir: string;
}

export interface ServerHandle {
  url: string;
  close: () => Promise<void>;
}

export async function createServer(options: ServerOptions): Promise<ServerHandle> {
  const fixtures = await loadFixtures(options.fixturesDir);

  const httpServer = createHttpServer((req, res) => {
    handleRequest(req, res, fixtures);
  });

  const wsServer = new WebSocketServer({ noServer: true });

  httpServer.on('upgrade', (req, socket, head) => {
    const url = req.url ?? '';
    if (!url.startsWith('/__mock_ws/')) {
      socket.destroy();
      return;
    }
    const name = url.slice('/__mock_ws/'.length);
    const scriptPath = fixtures.ws.get(name);
    wsServer.handleUpgrade(req, socket, head, (ws) => {
      if (!scriptPath) {
        ws.close(1008, 'script not found');
        return;
      }
      void streamWs(ws, scriptPath);
    });
  });

  await new Promise<void>((resolve) => {
    httpServer.listen(options.port, '127.0.0.1', () => resolve());
  });

  const address = httpServer.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}`;

  return {
    url,
    close: () =>
      new Promise<void>((resolve, reject) => {
        wsServer.close();
        httpServer.close((err) => (err ? reject(err) : resolve()));
      }),
  };
}

function handleRequest(
  req: IncomingMessage,
  res: ServerResponse,
  fixtures: FixtureSet,
): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  if (req.method === 'GET' && req.url?.startsWith('/__mock_sse/')) {
    const name = req.url.slice('/__mock_sse/'.length);
    const scriptPath = fixtures.sse.get(name);
    if (!scriptPath) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'sse script not found', name }));
      return;
    }
    void streamSse(res, scriptPath);
    return;
  }

  const method = (req.method ?? 'GET') as HttpMethod;
  const fixture = matchRest(method, req.url ?? '/', fixtures);
  if (fixture) {
    res.writeHead(fixture.status, { 'Content-Type': fixture.contentType });
    res.end(Buffer.from(fixture.body));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
