
from typecast.models import LanguageCode, TTSRequest


class TestLanguageCode:
    def test_language_code_enum_values(self):
        """Test that language codes have correct values"""
        assert LanguageCode.ENG == "eng"
        assert LanguageCode.KOR == "kor"
        assert LanguageCode.JPN == "jpn"
        assert LanguageCode.ZHO == "zho"
        assert LanguageCode.SPA == "spa"
        assert LanguageCode.FRA == "fra"
        assert LanguageCode.DEU == "deu"

    def test_language_code_in_request_with_enum(self):
        """Test that language code enum can be used in TTSRequest"""
        request = TTSRequest(
            text="Hello",
            voice_id="tc_test",
            model="ssfm-v21",
            language=LanguageCode.ENG,
        )

        assert request.language == LanguageCode.ENG
        assert request.language.value == "eng"

    def test_language_code_in_request_with_string(self):
        """Test that plain string can still be used for language"""
        request = TTSRequest(
            text="Hello",
            voice_id="tc_test",
            model="ssfm-v21",
            language="eng",
        )

        assert request.language == "eng"

    def test_language_code_optional(self):
        """Test that language is optional in TTSRequest"""
        request = TTSRequest(
            text="Hello",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        assert request.language is None

    def test_all_language_codes_exist(self):
        """Test that all documented language codes exist"""
        expected_codes = [
            "eng",
            "kor",
            "spa",
            "deu",
            "fra",
            "ita",
            "pol",
            "nld",
            "rus",
            "jpn",
            "ell",
            "tam",
            "tgl",
            "fin",
            "zho",
            "slk",
            "ara",
            "hrv",
            "ukr",
            "ind",
            "dan",
            "swe",
            "msa",
            "ces",
            "por",
            "bul",
            "ron",
        ]

        for code in expected_codes:
            # Check that enum value exists and has correct value
            lang_code = LanguageCode(code)
            assert lang_code.value == code

    def test_language_code_serialization(self):
        """Test that language code serializes correctly in request"""
        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
            language=LanguageCode.KOR,
        )

        # Test model_dump includes language as string value
        dumped = request.model_dump(exclude_none=True)
        assert dumped["language"] == "kor"
