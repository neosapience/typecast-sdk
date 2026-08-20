export interface ClientConfig {
  baseHost: string;
  apiKey?: string;
  source?: 'llms' | 'skill' | 'api-page' | 'api-docs';
  generatedBy?: string;
}
