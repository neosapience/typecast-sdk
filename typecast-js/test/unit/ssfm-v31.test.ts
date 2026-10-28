import { describe, expect, it } from 'vitest';
import type { TTSModel, TTSRequest } from '../../src/types/TextToSpeech';

describe('SSFM 3.1', () => {
  it('accepts the explicit model without replacing legacy model values', () => {
    const models: TTSModel[] = ['ssfm-v21', 'ssfm-v30', 'ssfm-v31'];
    for (const model of models) {
      const request: TTSRequest = { voice_id: 'tc_test', text: 'hello', model };
      expect(JSON.parse(JSON.stringify(request)).model).toBe(model);
    }
  });
});
