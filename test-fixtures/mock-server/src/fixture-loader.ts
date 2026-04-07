import { readdir, readFile } from 'node:fs/promises';
import { join, parse } from 'node:path';
import type { FixtureSet, HttpMethod, RestFixture } from './types.ts';

const REST_DIRS = ['voices', 'tts'] as const;
const CONTENT_TYPE_BY_EXT: Record<string, string> = {
  '.json': 'application/json',
  '.bin': 'application/octet-stream',
  '.txt': 'text/plain',
};

export async function loadFixtures(rootDir: string): Promise<FixtureSet> {
  const set: FixtureSet = {
    rest: new Map(),
    sse: new Map(),
    ws: new Map(),
  };

  for (const dir of REST_DIRS) {
    await loadRestDir(set, rootDir, dir);
  }
  await loadStreamDir(set.sse, join(rootDir, 'sse'));
  await loadStreamDir(set.ws, join(rootDir, 'ws'));

  return set;
}

async function loadRestDir(
  set: FixtureSet,
  rootDir: string,
  dirName: string,
): Promise<void> {
  const dir = join(rootDir, dirName);
  let entries: string[];
  try {
    entries = await readdir(dir);
  } catch {
    return;
  }
  // Sort so that when two filenames map to the same REST key (e.g.,
  // `list-200.json` and `list-401.json`), the higher-status variant
  // wins deterministically across operating systems.
  entries.sort((a, b) => a.localeCompare(b));
  for (const entry of entries) {
    const parsed = parse(entry);
    const match = parsed.name.match(/^(.+)-(\d{3})$/);
    if (!match) continue;
    const [, endpoint, statusStr] = match;
    const status = Number(statusStr);
    const contentType = CONTENT_TYPE_BY_EXT[parsed.ext] ?? 'application/octet-stream';
    const body = new Uint8Array(await readFile(join(dir, entry)));
    const fixture: RestFixture = {
      method: 'GET' satisfies HttpMethod,
      path: `/${dirName}/${endpoint}`,
      status,
      contentType,
      body,
    };
    set.rest.set(`${fixture.method} ${fixture.path}`, fixture);
  }
}

async function loadStreamDir(
  target: Map<string, string>,
  dir: string,
): Promise<void> {
  let entries: string[];
  try {
    entries = await readdir(dir);
  } catch {
    return;
  }
  entries.sort((a, b) => a.localeCompare(b));
  for (const entry of entries) {
    const parsed = parse(entry);
    target.set(parsed.name, join(dir, entry));
  }
}
