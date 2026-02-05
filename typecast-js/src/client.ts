import { ClientConfig, TTSRequest, TTSResponse, ApiErrorResponse } from './types';
import { VoicesResponse, VoiceV2Response, VoicesV2Filter } from './types/Voices';
import { TypecastAPIError } from './errors';

export class TypecastClient {
  private baseHost: string;
  private headers: Record<string, string>;

  constructor(config: Partial<ClientConfig> = {}) {
    const finalConfig: ClientConfig = {
      baseHost: process.env.TYPECAST_API_HOST || 'https://api.typecast.ai',
      apiKey: process.env.TYPECAST_API_KEY || '',
      ...config,
    };
    this.baseHost = finalConfig.baseHost;
    this.headers = {
      'X-API-KEY': finalConfig.apiKey,
      'Content-Type': 'application/json',
    };
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
      throw TypecastAPIError.fromResponse(
        response.status,
        response.statusText,
        errorData
      );
    }
    return response.json() as Promise<T>;
  }

  /**
   * Build URL with query parameters
   */
  private buildUrl(path: string, params?: Record<string, unknown>): string {
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
      throw TypecastAPIError.fromResponse(
        response.status,
        response.statusText,
        errorData
      );
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
   * Get available voices (V1 API)
   * @param model - Optional model filter (e.g., 'ssfm-v21', 'ssfm-v30')
   * @returns List of available voices with their emotions
   * @deprecated Use getVoicesV2() for enhanced metadata and filtering options
   */
  async getVoices(model?: string): Promise<VoicesResponse[]> {
    const response = await fetch(
      this.buildUrl('/v1/voices', model ? { model } : undefined),
      { headers: this.headers }
    );
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
      { headers: this.headers }
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
      this.buildUrl('/v2/voices', filter as Record<string, unknown>),
      { headers: this.headers }
    );
    return this.handleResponse<VoiceV2Response[]>(response);
  }

  /**
   * Get a specific voice by ID with enhanced metadata (V2 API)
   * @param voiceId - The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')
   * @returns Voice information with model-grouped emotions and metadata
   */
  async getVoiceV2(voiceId: string): Promise<VoiceV2Response> {
    const response = await fetch(
      this.buildUrl(`/v2/voices/${voiceId}`),
      { headers: this.headers }
    );
    return this.handleResponse<VoiceV2Response>(response);
  }
}
