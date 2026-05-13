import { promises as fs } from 'node:fs';
import path from 'node:path';

export const CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024;
const NAME_MIN_LENGTH = 1;
const NAME_MAX_LENGTH = 30;

export interface CustomVoice {
  voiceId: string;
  name: string;
  model: string;
}

export type CloneVoiceAudio = string | Uint8Array | Buffer | Blob | File;

export interface CloneVoiceRequest {
  audio: CloneVoiceAudio;
  name: string;
  model: string;
}

export interface ValidatedCloneInputs {
  audioBytes: Uint8Array;
  filename: string;
}

/**
 * Sync validation for non-path inputs (Uint8Array / Buffer).
 * For string path / Blob / File, use {@link validateCloneInputsAsync}.
 */
export function validateCloneInputs(
  audio: Uint8Array | Buffer,
  name: string,
): ValidatedCloneInputs {
  validateName(name);
  if (audio.byteLength > CLONING_MAX_FILE_SIZE) {
    throw new Error(`audio file exceeds 25MB limit; got ${audio.byteLength} bytes`);
  }
  // Buffer extends Uint8Array at runtime; wrap it in a plain Uint8Array so
  // callers always receive a canonical type regardless of input subclass.
  const audioBytes = Buffer.isBuffer(audio) ? new Uint8Array(audio) : audio;
  return { audioBytes, filename: 'audio.wav' };
}

/**
 * Async validation that handles all CloneVoiceAudio variants, including
 * string paths (read via Node fs) and Blob/File (read via .arrayBuffer()).
 * Returns the resolved bytes plus a filename suitable for multipart.
 */
export async function validateCloneInputsAsync(
  audio: CloneVoiceAudio,
  name: string,
): Promise<ValidatedCloneInputs> {
  validateName(name);
  if (typeof audio === 'string') {
    let bytes: Buffer;
    try {
      bytes = await fs.readFile(audio);
    } catch (err) {
      const code = (err as NodeJS.ErrnoException)?.code;
      if (code === 'ENOENT') {
        throw new Error(`audio file not found: ${audio}`);
      }
      throw err;
    }
    if (bytes.byteLength > CLONING_MAX_FILE_SIZE) {
      throw new Error(`audio file exceeds 25MB limit; got ${bytes.byteLength} bytes`);
    }
    return { audioBytes: new Uint8Array(bytes), filename: path.basename(audio) };
  }
  if (audio instanceof Uint8Array || Buffer.isBuffer(audio)) {
    return validateCloneInputs(audio, name);
  }
  if (typeof File !== 'undefined' && audio instanceof File) {
    if (audio.size > CLONING_MAX_FILE_SIZE) {
      throw new Error(`audio file exceeds 25MB limit; got ${audio.size} bytes`);
    }
    const buf = new Uint8Array(await audio.arrayBuffer());
    return { audioBytes: buf, filename: audio.name || 'audio.wav' };
  }
  if (typeof Blob !== 'undefined' && audio instanceof Blob) {
    if (audio.size > CLONING_MAX_FILE_SIZE) {
      throw new Error(`audio file exceeds 25MB limit; got ${audio.size} bytes`);
    }
    const buf = new Uint8Array(await audio.arrayBuffer());
    return { audioBytes: buf, filename: 'audio.wav' };
  }
  throw new TypeError('audio must be Uint8Array, Buffer, Blob, File, or string path');
}

function validateName(name: string): void {
  const charCount = Array.from(name).length;
  if (charCount < NAME_MIN_LENGTH || charCount > NAME_MAX_LENGTH) {
    throw new Error(
      `name must be ${NAME_MIN_LENGTH}-${NAME_MAX_LENGTH} characters; got ${charCount}`,
    );
  }
}

export function guessAudioMime(filename: string): string {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.wav')) return 'audio/wav';
  if (lower.endsWith('.mp3')) return 'audio/mpeg';
  return 'application/octet-stream';
}
