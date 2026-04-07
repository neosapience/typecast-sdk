import { createServer as createHttpServer, IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { loadFixtures } from './fixture-loader.ts';

export interface ServerOptions {
  port: number;
  fixturesDir: string;
}

export interface ServerHandle {
  url: string;
  close: () => Promise<void>;
}

export async function createServer(options: ServerOptions): Promise<ServerHandle> {
  await loadFixtures(options.fixturesDir);

  const httpServer = createHttpServer((req, res) => {
    handleRequest(req, res);
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
        httpServer.close((err) => (err ? reject(err) : resolve()));
      }),
  };
}

function handleRequest(req: IncomingMessage, res: ServerResponse): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
