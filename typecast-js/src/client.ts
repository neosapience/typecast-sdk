import {
  ClientConfig,
  GenerateToFileRequest,
  TTSRequest,
  TTSResponse,
  TTSRequestStream,
  ApiErrorResponse,
} from './types';
import { SubscriptionResponse } from './types/Subscription';
import { VoicesResponse, VoiceV2Response, VoiceV3Response, VoicesV2Filter, RecommendedVoice } from './types/Voices';
import { TypecastAPIError } from './errors';
import {
  TTSRequestWithTimestamps,
  TTSWithTimestampsResponse,
  WithTimestampsResult,
} from './types/Timestamps';
import {
  type CloneVoiceRequest,
  type CustomVoice,
  type ProfessionalCloneVoiceRequest,
  guessAudioMime,
  validateCloneInputsAsync,
} from './types/QuickCloning';
import { SpeechComposer, type ComposeSegment } from './composer';

const SDK_VERSION = '0.4.12';
const DEFAULT_BASE_HOST = 'https://api.typecast.ai';
type QueryParam = string | number | boolean | null | undefined;

export class TypecastClient {
  private baseHost: string;
  private headers: Record<string, string>;

  constructor(config: Partial<ClientConfig> = {}) {
    const finalConfig: ClientConfig = {
      baseHost: process.env.TYPECAST_API_HOST || DEFAULT_BASE_HOST,
      apiKey: process.env.TYPECAST_API_KEY || '',
      ...config,
    };
    const apiKey = (finalConfig.apiKey || '').trim();
    this.baseHost = TypecastClient.normalizeBaseHost(finalConfig.baseHost);
    this.headers = {
      'Content-Type': 'application/json',
      'User-Agent': TypecastClient.buildUserAgent(
        this.baseHost,
        finalConfig.source,
        finalConfig.generatedBy,
      ),
    };
    if (apiKey) {
      this.headers['X-API-KEY'] = apiKey;
    }
  }

  private static normalizeBaseHost(baseHost: string): string {
    let normalized = baseHost.trim();
    while (normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1);
    }
    return normalized;
  }

  private static buildUserAgent(
    baseHost: string,
    source?: 'llms' | 'skill' | 'api-page' | 'api-docs',
    generatedBy?: string,
  ): string {
    const nodeVersion = process.versions.node.split('.').slice(0, 2).join('.');
    const base = baseHost.toLowerCase() === DEFAULT_BASE_HOST ? 'default' : 'custom';
    const os = TypecastClient.normalizeOS(process.platform);
    const arch = TypecastClient.normalizeArch(process.arch);
    return `typecast-js/${SDK_VERSION} Node/${nodeVersion} fetch (runtime=node; base=${base}; os=${os}; arch=${arch}; sdk_env=node; platform=server)${TypecastClient.attributionSuffix(source, generatedBy)}`;
  }

  private static attributionSuffix(source?: string, generatedBy?: string): string {
    if (source === undefined && generatedBy === undefined) return '';
    if (
      !['llms', 'skill', 'api-page', 'api-docs'].includes(source || '') ||
      typeof generatedBy !== 'string' ||
      !generatedBy
    ) {
      throw new Error(
        'source (llms, skill, api-page, or api-docs) and generatedBy must be provided together',
      );
    }
    if (!/^[a-z0-9][a-z0-9._-]{0,31}$/.test(generatedBy)) {
      throw new Error('generatedBy must be a lowercase token of at most 32 characters');
    }
    return ` typecast-integration/1 (source=${source}; generated_by=${generatedBy})`;
  }

  private static normalizeOS(os: string): string {
    if (os === 'darwin') return 'macos';
    if (os === 'win32') return 'windows';
    return os || 'unknown';
  }

  private static normalizeArch(arch: string): string {
    if (arch === 'x64') return 'x64';
    if (arch === 'arm64') return 'arm64';
    if (arch === 'ia32') return 'x86';
    return arch || 'unknown';
  }

  /**
   * Handle HTTP error responses
   */
  private async handleResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
      let errorData: ApiErrorResponse | undefined;
      try {
        errorData = (await response.json()) as ApiErrorResponse;
      } catch {
        // Response body is not JSON
      }
      throw TypecastAPIError.fromResponse(response.status, response.statusText, errorData);
    }
    return response.json() as Promise<T>;
  }

  /**
   * Build URL with query parameters
   */
  private buildUrl(path: string, params?: Record<string, QueryParam>): string {
    const url = new URL(path, this.baseHost);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null) {
          url.searchParams.append(key, String(value));
        }
      });
    }
    return url.toString();
  }

  /**
   * Convert text to speech
   * @param request - TTS request parameters including text, voice_id, model, and optional settings
   * @returns TTSResponse containing audio data, duration, and format
   */
  async textToSpeech(request: TTSRequest): Promise<TTSResponse> {
    const response = await fetch(this.buildUrl('/v1/text-to-speech'), {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      let errorData: ApiErrorResponse | undefined;
      try {
        errorData = (await response.json()) as ApiErrorResponse;
      } catch {
        // Response body is not JSON
      }
      throw TypecastAPIError.fromResponse(response.status, response.statusText, errorData);
    }

    const contentType = response.headers.get('content-type') || 'audio/wav';
    const formatFromHeader = contentType.split('/')[1] || 'wav';
    const format: 'wav' | 'mp3' = formatFromHeader === 'mp3' ? 'mp3' : 'wav';

    const durationHeader = response.headers.get('x-audio-duration');
    const duration = durationHeader ? Number(durationHeader) : 0;

    const audioData = await response.arrayBuffer();

    return {
      audioData,
      duration,
      format,
    };
  }

  /**
   * Build composed speech from multiple text and pause segments.
   *
   * Text passed to `.say()` may include pause markup such as `<|0.3s|>`.
   * `.pause(seconds)` also uses seconds, e.g. `0.3` for 300 ms.
   */
  composeSpeech(): SpeechComposer {
    return new SpeechComposer((segments) => this.composeTextToSpeech(segments));
  }

  async composeTextToSpeech(segments: ComposeSegment[]): Promise<TTSResponse> {
    const response = await fetch(this.buildUrl('/v1/text-to-speech/compose'), {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ segments }),
    });
    if (!response.ok) return this.handleResponse<TTSResponse>(response);
    const contentType = response.headers.get('content-type') || 'audio/wav';
    return {
      audioData: await response.arrayBuffer(),
      duration: Number(response.headers.get('x-audio-duration') || 0),
      format: contentType.includes('mp3') || contentType.includes('mpeg') ? 'mp3' : 'wav',
    };
  }

  /**
   * Convert text to speech and write the audio bytes to a local file.
   *
   * `model` defaults to `ssfm-v30`. If `request.output.audio_format` is omitted,
   * the format is inferred from a `.mp3` or `.wav` file extension.
   *
   * @param path - Destination file path
   * @param request - TTS request with required text and voice_id
   * @returns TTSResponse containing audio data, duration, and format
   */
  async generateToFile(path: string, request: GenerateToFileRequest): Promise<TTSResponse> {
    if (!path.trim()) {
      throw new Error('path cannot be empty');
    }
    const { writeFile } = await import('node:fs/promises');
    const audioFormat = TypecastClient.inferAudioFormatFromPath(path);
    const output =
      audioFormat && !request.output?.audio_format
        ? { ...request.output, audio_format: audioFormat }
        : request.output;
    const response = await this.textToSpeech({
      ...request,
      model: request.model ?? 'ssfm-v30',
      output,
    });
    await writeFile(path, Buffer.from(response.audioData));
    return response;
  }

  private static inferAudioFormatFromPath(path: string): 'wav' | 'mp3' | undefined {
    const lower = path.toLowerCase();
    if (lower.endsWith('.mp3')) return 'mp3';
    if (lower.endsWith('.wav')) return 'wav';
    return undefined;
  }

  /**
   * Convert text to speech and receive the audio as a chunked binary stream.
   *
   * For WAV the first chunk contains a streaming WAV header (with size declared
   * as 0xFFFFFFFF) followed by PCM data; subsequent chunks are raw PCM. For MP3
   * each chunk contains independently-decodable MP3 frames.
   *
   * @param request - TTS streaming request parameters
   * @returns A `ReadableStream` of `Uint8Array` chunks containing the audio
   */
  async textToSpeechStream(request: TTSRequestStream): Promise<ReadableStream<Uint8Array>> {
    const response = await fetch(this.buildUrl('/v1/text-to-speech/stream'), {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      let errorData: ApiErrorResponse | undefined;
      try {
        errorData = (await response.json()) as ApiErrorResponse;
      } catch {
        // Response body is not JSON
      }
      throw TypecastAPIError.fromResponse(response.status, response.statusText, errorData);
    }

    if (!response.body) {
      throw new TypecastAPIError('Streaming response body was empty', 500);
    }

    return response.body;
  }

  /**
   * Synthesize speech and return base64 audio + alignment timestamps.
   *
   * @param request - Same shape as `TTSRequest` (voice_id, text, model, etc.).
   * @param options - Optional `granularity: 'word' | 'char'`.
   * @returns A `WithTimestampsResult` with `audioBytes`, `toSrt()`, `toVtt()`,
   *   `saveAudio()` helpers.
   */
  async textToSpeechWithTimestamps(
    request: TTSRequestWithTimestamps,
    options: { granularity?: 'word' | 'char' } = {},
  ): Promise<WithTimestampsResult> {
    const { granularity } = options;
    if (granularity !== undefined && granularity !== 'word' && granularity !== 'char') {
      throw new Error(
        `granularity must be undefined, 'word', or 'char'; got ${String(granularity)}`,
      );
    }
    const params = granularity ? { granularity } : undefined;
    const url = this.buildUrl('/v1/text-to-speech/with-timestamps', params);
    const response = await fetch(url, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify(request),
    });
    const data = await this.handleResponse<TTSWithTimestampsResponse>(response);
    return new WithTimestampsResult(data);
  }

  /**
   * Get available voices (V1 API)
   * @param model - Optional model filter (e.g., 'ssfm-v21', 'ssfm-v30')
   * @returns List of available voices with their emotions
   * @deprecated Use getVoicesV2() for enhanced metadata and filtering options
   */
  async getVoices(model?: string): Promise<VoicesResponse[]> {
    const response = await fetch(this.buildUrl('/v1/voices', model ? { model } : undefined), {
      headers: this.headers,
    });
    return this.handleResponse<VoicesResponse[]>(response);
  }

  /**
   * Get voice by ID (V1 API)
   * @param voiceId - The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')
   * @param model - Optional model filter
   * @returns Voice information including available emotions
   * @deprecated Use getVoicesV2() for enhanced metadata
   */
  async getVoiceById(voiceId: string, model?: string): Promise<VoicesResponse[]> {
    const response = await fetch(
      this.buildUrl(`/v1/voices/${voiceId}`, model ? { model } : undefined),
      { headers: this.headers },
    );
    return this.handleResponse<VoicesResponse[]>(response);
  }

  /**
   * Get voices with enhanced metadata (V2 API)
   * Returns voices with model-grouped emotions and additional metadata
   * @param filter - Optional filter options (model, gender, age, use_cases)
   */
  async getVoicesV2(filter?: VoicesV2Filter): Promise<VoiceV2Response[]> {
    const response = await fetch(
      this.buildUrl('/v2/voices', filter as Record<string, QueryParam>),
      { headers: this.headers },
    );
    return this.handleResponse<VoiceV2Response[]>(response);
  }

  /**
   * Get a specific voice by ID with enhanced metadata (V2 API)
   * @param voiceId - The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')
   * @returns Voice information with model-grouped emotions and metadata
   */
  async getVoiceV2(voiceId: string): Promise<VoiceV2Response> {
    const response = await fetch(this.buildUrl(`/v2/voices/${voiceId}`), { headers: this.headers });
    return this.handleResponse<VoiceV2Response>(response);
  }

  /** Get voices using the current V3 Voice API. */
  async getVoicesV3(filter?: VoicesV2Filter): Promise<VoiceV3Response[]> {
    const response = await fetch(
      this.buildUrl('/v3/voices', filter as Record<string, QueryParam>),
      { headers: this.headers },
    );
    return this.handleResponse<VoiceV3Response[]>(response);
  }

  /** Get a voice by ID using the current V3 Voice API. */
  async getVoiceV3(voiceId: string): Promise<VoiceV3Response> {
    const response = await fetch(this.buildUrl(`/v3/voices/${encodeURIComponent(voiceId)}`), {
      headers: this.headers,
    });
    return this.handleResponse<VoiceV3Response>(response);
  }

  /**
   * Recommend voices from a text description.
   *
   * Results only contain voice_id, voice_name, and similarity score. Use
   * getVoiceV2() or getVoicesV2() when you need detailed metadata for the
   * returned voice IDs.
   *
   * @param query - Text description of the desired voice
   * @param count - Number of recommendations to return, from 1 to 10. Defaults to 5.
   */
  async recommendVoices(query: string, count = 5): Promise<RecommendedVoice[]> {
    if (count < 1 || count > 10) {
      throw new RangeError('count must be between 1 and 10');
    }
    const response = await fetch(this.buildUrl('/v1/voices/recommendations', { query, count }), {
      headers: this.headers,
    });
    return this.handleResponse<RecommendedVoice[]>(response);
  }

  /**
   * Get the authenticated user's current subscription information.
   * Returns plan tier, credit usage, and concurrency limits. Use this to
   * check remaining credits or verify your plan before making TTS calls.
   */
  async getMySubscription(): Promise<SubscriptionResponse> {
    const response = await fetch(this.buildUrl('/v1/users/me/subscription'), {
      headers: this.headers,
    });
    return this.handleResponse<SubscriptionResponse>(response);
  }

  /**
   * Create a quick-cloned custom voice from an audio sample.
   *
   * The cloned voice ID has a `uc_` prefix and works directly with
   * `textToSpeech` / `textToSpeechWithTimestamps` etc.
   *
   * @param req.audio  Audio sample. Accepts string path (Node), Uint8Array,
   *                   Buffer, Blob, or File. Max 25 MB.
   * @param req.name   Voice name, 1-30 characters.
   * @param req.model  Engine model: `"ssfm-v21"` or `"ssfm-v30"`.
   * @returns The created `CustomVoice` (`voiceId` has `uc_` prefix).
   * @throws  Error on validation failure or non-2xx response.
   */
  async cloneVoice(req: CloneVoiceRequest): Promise<CustomVoice> {
    const { audioBytes, filename } = await validateCloneInputsAsync(req.audio, req.name);
    if (req.model !== 'ssfm-v21' && req.model !== 'ssfm-v30') {
      throw new TypeError(`model must be 'ssfm-v21' or 'ssfm-v30'; got ${String(req.model)}`);
    }
    // Copy to a plain ArrayBuffer so TypeScript accepts it as a valid BlobPart.
    // (Uint8Array may reference a SharedArrayBuffer which is not BlobPart-compatible.)
    const audioBuffer = audioBytes.buffer.slice(
      audioBytes.byteOffset,
      audioBytes.byteOffset + audioBytes.byteLength,
    ) as ArrayBuffer;
    const form = new FormData();
    form.append('name', req.name);
    form.append('model', req.model);
    form.append('file', new Blob([audioBuffer], { type: guessAudioMime(filename) }), filename);

    // Strip Content-Type so fetch can set multipart/form-data with boundary.
    const headers = { ...this.headers };
    delete headers['Content-Type'];

    const response = await fetch(this.buildUrl('/v1/custom-voices/instant-clone'), {
      method: 'POST',
      headers,
      body: form,
    });
    const body = await this.handleResponse<{ voice_id: string; name: string; model: string }>(
      response,
    );
    return { voiceId: body.voice_id, name: body.name, model: body.model };
  }

  /**
   * Soft-delete a custom voice by ID.
   *
   * @param voiceId Voice identifier with `uc_` prefix.
   * @throws Error on non-2xx response (e.g., 404 if not owned or already deleted).
   */
  async deleteVoice(voiceId: string): Promise<void> {
    if (!voiceId || !voiceId.startsWith('uc_')) {
      throw new TypeError(`voiceId must start with 'uc_'; got ${String(voiceId)}`);
    }
    const response = await fetch(this.buildUrl(`/v1/custom-voices/${encodeURIComponent(voiceId)}`), {
      method: 'DELETE',
      headers: this.headers,
    });
    /* c8 ignore start */ // handleResponse always throws on !ok; the post-call brace is unreachable
    if (!response.ok) {
      await this.handleResponse(response);
    }
    /* c8 ignore stop */
    // 204 No Content: nothing to return.
  }

  /** Start an asynchronous professional custom-voice clone. */
  async createProfessionalVoice(req: ProfessionalCloneVoiceRequest): Promise<CustomVoice> {
    const { audioBytes, filename } = await validateCloneInputsAsync(req.audio, req.name);
    if (req.model !== 'ssfm-v21' && req.model !== 'ssfm-v30') {
      throw new TypeError(`model must be 'ssfm-v21' or 'ssfm-v30'; got ${String(req.model)}`);
    }
    const audioBuffer = audioBytes.buffer.slice(audioBytes.byteOffset, audioBytes.byteOffset + audioBytes.byteLength) as ArrayBuffer;
    const form = new FormData();
    form.append('name', req.name);
    form.append('language', req.language);
    form.append('model', req.model);
    form.append('files', new Blob([audioBuffer], { type: guessAudioMime(filename) }), filename);
    const headers = { ...this.headers };
    delete headers['Content-Type'];
    const body = await this.handleResponse<Record<string, string | null>>(
      await fetch(this.buildUrl('/v1/custom-voices/professional-clone'), { method: 'POST', headers, body: form }),
    );
    return this.customVoice(body);
  }

  /** List custom voices owned by the authenticated user. */
  async getCustomVoices(): Promise<CustomVoice[]> {
    const body = await this.handleResponse<Record<string, string | null>[]>(
      await fetch(this.buildUrl('/v1/custom-voices'), { headers: this.headers }),
    );
    return body.map((voice) => this.customVoice(voice));
  }

  /** Get a custom voice, including professional-clone status. */
  async getCustomVoice(voiceId: string): Promise<CustomVoice> {
    if (!voiceId || !voiceId.startsWith('uc_')) {
      throw new TypeError(`voiceId must start with 'uc_'; got ${String(voiceId)}`);
    }
    const body = await this.handleResponse<Record<string, string | null>>(
      await fetch(this.buildUrl(`/v1/custom-voices/${encodeURIComponent(voiceId)}`), { headers: this.headers }),
    );
    return this.customVoice(body);
  }

  private customVoice(body: Record<string, string | null>): CustomVoice {
    return {
      voiceId: body.voice_id as string,
      name: body.name as string,
      model: body.model as string,
      source: body.source ?? undefined,
      status: body.status ?? undefined,
      error: body.error,
      createdAt: body.created_at ?? undefined,
    };
  }
}
