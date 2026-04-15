<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\OutputStream;
use Neosapience\Typecast\Models\PresetPrompt;
use Neosapience\Typecast\Models\Prompt;
use Neosapience\Typecast\Models\SmartPrompt;
use Neosapience\Typecast\Models\SubscriptionResponse;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\Models\Voice;
use Neosapience\Typecast\Models\VoiceV2;
use Neosapience\Typecast\Models\VoicesV2Filter;
use PHPUnit\Framework\TestCase;

class ModelsTest extends TestCase
{
    public function testPromptToArray(): void
    {
        $prompt = new Prompt(emotionPreset: 'happy', emotionIntensity: 1.5);
        $arr = $prompt->toArray();

        $this->assertSame('happy', $arr['emotion_preset']);
        $this->assertSame(1.5, $arr['emotion_intensity']);
    }

    public function testPromptToArrayDefaultValues(): void
    {
        $prompt = new Prompt();
        $arr = $prompt->toArray();

        $this->assertSame('normal', $arr['emotion_preset']);
        $this->assertSame(1.0, $arr['emotion_intensity']);
    }

    public function testPresetPromptToArray(): void
    {
        $prompt = new PresetPrompt(emotionPreset: 'angry', emotionIntensity: 2.0);
        $arr = $prompt->toArray();

        $this->assertSame('preset', $arr['emotion_type']);
        $this->assertSame('angry', $arr['emotion_preset']);
        $this->assertSame(2.0, $arr['emotion_intensity']);
    }

    public function testSmartPromptToArray(): void
    {
        $prompt = new SmartPrompt(previousText: 'Hello', nextText: 'Goodbye');
        $arr = $prompt->toArray();

        $this->assertSame('smart', $arr['emotion_type']);
        $this->assertSame('Hello', $arr['previous_text']);
        $this->assertSame('Goodbye', $arr['next_text']);
    }

    public function testSmartPromptToArrayNullFields(): void
    {
        $prompt = new SmartPrompt();
        $arr = $prompt->toArray();

        $this->assertSame('smart', $arr['emotion_type']);
        $this->assertArrayNotHasKey('previous_text', $arr);
        $this->assertArrayNotHasKey('next_text', $arr);
    }

    public function testOutputToArray(): void
    {
        $output = new Output(volume: 150, audioPitch: 3, audioTempo: 1.5, audioFormat: 'mp3');
        $arr = $output->toArray();

        $this->assertSame(150, $arr['volume']);
        $this->assertSame(3, $arr['audio_pitch']);
        $this->assertSame(1.5, $arr['audio_tempo']);
        $this->assertSame('mp3', $arr['audio_format']);
    }

    public function testOutputToArrayDefaultsExcluded(): void
    {
        $output = new Output();
        $arr = $output->toArray();

        $this->assertSame(100, $arr['volume']);
        $this->assertArrayNotHasKey('audio_pitch', $arr);
        $this->assertArrayNotHasKey('audio_tempo', $arr);
        $this->assertSame('wav', $arr['audio_format']);
    }

    public function testOutputToArrayWithTargetLufs(): void
    {
        $output = new Output(targetLufs: -14.0);
        $arr = $output->toArray();

        $this->assertSame(-14.0, $arr['target_lufs']);
        $this->assertArrayNotHasKey('volume', $arr);
    }

    public function testOutputStreamToArray(): void
    {
        $output = new OutputStream(audioPitch: -2, audioTempo: 0.8, audioFormat: 'mp3');
        $arr = $output->toArray();

        $this->assertSame(-2, $arr['audio_pitch']);
        $this->assertSame(0.8, $arr['audio_tempo']);
        $this->assertSame('mp3', $arr['audio_format']);
        $this->assertArrayNotHasKey('volume', $arr);
        $this->assertArrayNotHasKey('target_lufs', $arr);
    }

    public function testOutputStreamToArrayDefaultsExcluded(): void
    {
        $output = new OutputStream();
        $arr = $output->toArray();

        $this->assertArrayNotHasKey('audio_pitch', $arr);
        $this->assertArrayNotHasKey('audio_tempo', $arr);
        $this->assertSame('wav', $arr['audio_format']);
    }

    public function testTTSRequestToArray(): void
    {
        $request = new TTSRequest(
            voiceId: 'tc_123',
            text: 'Hello world',
            model: 'ssfm-v21',
            language: 'eng',
            prompt: new Prompt(emotionPreset: 'happy'),
            output: new Output(volume: 100),
            seed: 42,
        );
        $arr = $request->toArray();

        $this->assertSame('tc_123', $arr['voice_id']);
        $this->assertSame('Hello world', $arr['text']);
        $this->assertSame('ssfm-v21', $arr['model']);
        $this->assertSame('eng', $arr['language']);
        $this->assertSame('happy', $arr['prompt']['emotion_preset']);
        $this->assertSame(42, $arr['seed']);
    }

    public function testTTSRequestToArrayMinimal(): void
    {
        $request = new TTSRequest(
            voiceId: 'tc_123',
            text: 'Hello',
            model: 'ssfm-v30',
        );
        $arr = $request->toArray();

        $this->assertSame('tc_123', $arr['voice_id']);
        $this->assertSame('Hello', $arr['text']);
        $this->assertSame('ssfm-v30', $arr['model']);
        $this->assertArrayNotHasKey('language', $arr);
        $this->assertArrayNotHasKey('prompt', $arr);
        $this->assertArrayNotHasKey('output', $arr);
        $this->assertArrayNotHasKey('seed', $arr);
    }

    public function testTTSRequestStreamToArray(): void
    {
        $request = new TTSRequestStream(
            voiceId: 'tc_456',
            text: 'Stream me',
            model: 'ssfm-v30',
            output: new OutputStream(audioFormat: 'mp3'),
        );
        $arr = $request->toArray();

        $this->assertSame('tc_456', $arr['voice_id']);
        $this->assertSame('Stream me', $arr['text']);
        $this->assertSame('ssfm-v30', $arr['model']);
        $this->assertSame('mp3', $arr['output']['audio_format']);
    }

    public function testSubscriptionResponseFromArray(): void
    {
        $data = [
            'plan' => 'plus',
            'credits' => [
                'plan_credits' => 10000,
                'used_credits' => 500,
            ],
            'limits' => [
                'concurrency_limit' => 5,
            ],
        ];
        $sub = SubscriptionResponse::fromArray($data);

        $this->assertSame('plus', $sub->plan);
        $this->assertSame(10000, $sub->planCredits);
        $this->assertSame(500, $sub->usedCredits);
        $this->assertSame(5, $sub->concurrencyLimit);
    }

    public function testVoiceFromArray(): void
    {
        $data = [
            'voice_id' => 'tc_abc',
            'voice_name' => 'Test Voice',
            'model' => 'ssfm-v21',
            'emotions' => ['normal', 'happy'],
        ];
        $voice = Voice::fromArray($data);

        $this->assertSame('tc_abc', $voice->voiceId);
        $this->assertSame('Test Voice', $voice->voiceName);
        $this->assertSame('ssfm-v21', $voice->model);
        $this->assertSame(['normal', 'happy'], $voice->emotions);
    }

    public function testVoiceV2FromArray(): void
    {
        $data = [
            'voice_id' => 'tc_xyz',
            'voice_name' => 'V2 Voice',
            'models' => [
                ['version' => 'ssfm-v30', 'emotions' => ['normal', 'happy', 'whisper']],
            ],
            'gender' => 'female',
            'age' => 'young_adult',
            'use_cases' => ['Podcast', 'Audiobook'],
        ];
        $voice = VoiceV2::fromArray($data);

        $this->assertSame('tc_xyz', $voice->voiceId);
        $this->assertSame('V2 Voice', $voice->voiceName);
        $this->assertCount(1, $voice->models);
        $this->assertSame('female', $voice->gender);
        $this->assertSame('young_adult', $voice->age);
        $this->assertSame(['Podcast', 'Audiobook'], $voice->useCases);
    }

    public function testVoiceV2FromArrayNullOptionals(): void
    {
        $data = [
            'voice_id' => 'tc_min',
            'voice_name' => 'Minimal',
            'models' => [],
        ];
        $voice = VoiceV2::fromArray($data);

        $this->assertNull($voice->gender);
        $this->assertNull($voice->age);
        $this->assertNull($voice->useCases);
    }

    public function testVoicesV2FilterToQueryParams(): void
    {
        $filter = new VoicesV2Filter(
            model: 'ssfm-v30',
            gender: 'female',
            age: 'young_adult',
            useCases: 'Podcast',
        );
        $params = $filter->toQueryParams();

        $this->assertSame('ssfm-v30', $params['model']);
        $this->assertSame('female', $params['gender']);
        $this->assertSame('young_adult', $params['age']);
        $this->assertSame('Podcast', $params['use_cases']);
    }

    public function testVoicesV2FilterToQueryParamsEmpty(): void
    {
        $filter = new VoicesV2Filter();
        $params = $filter->toQueryParams();

        $this->assertEmpty($params);
    }

    public function testVoicesV2FilterToQueryParamsPartial(): void
    {
        $filter = new VoicesV2Filter(model: 'ssfm-v21');
        $params = $filter->toQueryParams();

        $this->assertSame(['model' => 'ssfm-v21'], $params);
    }
}
