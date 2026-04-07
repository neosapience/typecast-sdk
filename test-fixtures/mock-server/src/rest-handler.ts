import type { FixtureSet, HttpMethod, RestFixture } from './types.ts';

export function matchRest(
  method: HttpMethod,
  pathWithQuery: string,
  fixtures: FixtureSet,
): RestFixture | null {
  const path = stripQuery(pathWithQuery);
  return fixtures.rest.get(`${method} ${path}`) ?? null;
}

function stripQuery(pathWithQuery: string): string {
  const i = pathWithQuery.indexOf('?');
  return i === -1 ? pathWithQuery : pathWithQuery.slice(0, i);
}
