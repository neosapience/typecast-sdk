export interface ClientConfig {
  baseHost: string;
  apiKey?: string;
  source?: 'llms' | 'skill';
  generatedBy?: string;
}
