<?php
declare(strict_types=1);
namespace Neosapience\Typecast\Tests\Unit;

use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\OutputStream;
use PHPUnit\Framework\TestCase;

final class RemoveSilenceTest extends TestCase
{
    public function testOptionalZeroAndRange(): void
    {
        foreach ([Output::class, OutputStream::class] as $class) {
            self::assertArrayNotHasKey('remove_silence_ms', (new $class())->toArray());
            foreach ([0, 300, 1000] as $value) {
                self::assertSame($value, (new $class(removeSilenceMs: $value))->toArray()['remove_silence_ms']);
            }
            foreach ([-1, 1001] as $value) {
                try {
                    (new $class(removeSilenceMs: $value))->toArray();
                    self::fail('Invalid silence accepted');
                } catch (\InvalidArgumentException $error) {
                    self::assertStringContainsString('removeSilenceMs', $error->getMessage());
                }
            }
        }
    }
}
