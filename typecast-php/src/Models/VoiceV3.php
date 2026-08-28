<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/** Voice from the current V3 Voice API. */
class VoiceV3
{
    /** @param array{eng: string, kor: string} $voiceName @param array<array{version: string, emotions: string[]}> $models */
    public function __construct(public string $voiceId, public array $voiceName, public array $models = [], public string $voiceType = '', public ?string $gender = null, public ?string $age = null, public ?array $useCases = null, public ?string $previewUrl = null) {}
    /** @param array<string,mixed> $data */
    public static function fromArray(array $data): self
    {
        return new self($data['voice_id'] ?? '', $data['voice_name'] ?? ['eng' => '', 'kor' => ''], $data['models'] ?? [], $data['voice_type'] ?? '', $data['gender'] ?? null, $data['age'] ?? null, $data['use_cases'] ?? null, $data['preview_url'] ?? null);
    }
}
