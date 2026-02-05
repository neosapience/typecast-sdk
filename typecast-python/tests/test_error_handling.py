import pytest

from typecast.client import Typecast
from typecast.exceptions import (
    BadRequestError,
    InternalServerError,
    NotFoundError,
    PaymentRequiredError,
    TypecastError,
    UnauthorizedError,
    UnprocessableEntityError,
)
from typecast.models import TTSRequest


@pytest.fixture
def typecast_client():
    return Typecast()


class TestErrorHandling:
    def test_unauthorized_error(self, mocker):
        # Arrange
        client = Typecast(api_key="invalid_key")
        mock_response = mocker.Mock()
        mock_response.status_code = 401
        mock_response.text = "Invalid API key"

        mocker.patch.object(client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(UnauthorizedError) as exc_info:
            client.text_to_speech(request)

        assert exc_info.value.status_code == 401
        assert "Unauthorized" in str(exc_info.value.message)

    def test_bad_request_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 400
        mock_response.text = "Invalid request parameters"

        mocker.patch.object(typecast_client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(BadRequestError) as exc_info:
            typecast_client.text_to_speech(request)

        assert exc_info.value.status_code == 400

    def test_payment_required_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 402
        mock_response.text = "Insufficient credits"

        mocker.patch.object(typecast_client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(PaymentRequiredError) as exc_info:
            typecast_client.text_to_speech(request)

        assert exc_info.value.status_code == 402

    def test_not_found_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 404
        mock_response.text = "Voice not found"

        mocker.patch.object(typecast_client.session, "get", return_value=mock_response)

        # Act & Assert
        with pytest.raises(NotFoundError) as exc_info:
            typecast_client.get_voice("tc_nonexistent")

        assert exc_info.value.status_code == 404

    def test_unprocessable_entity_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 422
        mock_response.text = "Validation error"

        mocker.patch.object(typecast_client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(UnprocessableEntityError) as exc_info:
            typecast_client.text_to_speech(request)

        assert exc_info.value.status_code == 422

    def test_internal_server_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 500
        mock_response.text = "Internal server error"

        mocker.patch.object(typecast_client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(InternalServerError) as exc_info:
            typecast_client.text_to_speech(request)

        assert exc_info.value.status_code == 500

    def test_generic_error(self, typecast_client, mocker):
        # Arrange
        mock_response = mocker.Mock()
        mock_response.status_code = 503
        mock_response.text = "Service unavailable"

        mocker.patch.object(typecast_client.session, "post", return_value=mock_response)

        request = TTSRequest(
            text="Test",
            voice_id="tc_test",
            model="ssfm-v21",
        )

        # Act & Assert
        with pytest.raises(TypecastError) as exc_info:
            typecast_client.text_to_speech(request)

        assert exc_info.value.status_code == 503
