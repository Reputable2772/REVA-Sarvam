import pytest
import os
from backend.app.services.sarvam_service import SarvamService

def test_sarvam_service_key_rotation(monkeypatch):
    monkeypatch.setenv("SARVAM_API_KEYS", "key1,key2,key3")
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    
    SarvamService._key_index = 0
    assert SarvamService.get_api_keys() == ["key1", "key2", "key3"]
    assert SarvamService.get_api_key() == "key1"
    
    rotated = SarvamService.rotate_api_key()
    assert rotated == "key2"
    assert SarvamService.get_api_key() == "key2"
    
    rotated = SarvamService.rotate_api_key()
    assert rotated == "key3"
    
    rotated = SarvamService.rotate_api_key()
    assert rotated == "key1"

def test_sarvam_service_openai_config(monkeypatch):
    monkeypatch.setenv("OPENAI_COMPATIBLE_API_KEY", "test-openai-key")
    monkeypatch.setenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.groq.com/openai/v1/")
    monkeypatch.setenv("OPENAI_COMPATIBLE_MODEL", "llama3-70b-8192")
    
    cfg = SarvamService.get_openai_fallback_config()
    assert cfg["api_key"] == "test-openai-key"
    assert cfg["base_url"] == "https://api.groq.com/openai/v1"
    assert cfg["model"] == "llama3-70b-8192"

def test_sarvam_service_transcription_simulation(monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEYS", raising=False)
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_COMPATIBLE_API_KEY", raising=False)
    
    res_kn = SarvamService.transcribe_audio(b"dummy", language_code="kn-IN")
    assert res_kn["success"] is True
    assert "ಟೊಮೆಟೊ" in res_kn["transcript"]
    assert res_kn["is_live_api"] is False

    res_en = SarvamService.transcribe_audio(b"dummy", language_code="en-IN")
    assert res_en["success"] is True
    assert "tomato" in res_en["transcript"].lower()

def test_sarvam_service_synthesize_speech_simulation(monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEYS", raising=False)
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    
    res = SarvamService.synthesize_speech("ನಮಸ್ಕಾರ", language_code="kn-IN")
    assert res["success"] is True
    assert "audio_base64" in res
    assert len(res["audio_base64"]) > 0

def test_sarvam_service_entity_extraction_heuristic(monkeypatch):
    monkeypatch.delenv("SARVAM_API_KEYS", raising=False)
    monkeypatch.delenv("SARVAM_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_COMPATIBLE_API_KEY", raising=False)
    
    res = SarvamService.extract_harvest_entities("ನನ್ನ ಬಳಿ 600 ಕೆಜಿ ಗ್ರೇಡ್ ಬಿ ಈರುಳ್ಳಿ ಇದೆ ಕೋಲಾರದಿಂದ")
    assert res["success"] is True
    extracted = res["extracted"]
    assert extracted["crop"] == "Onion"
    assert extracted["quantity_kg"] == 600.0
    assert extracted["quality_grade"] == "Grade B"
    assert extracted["village"] == "Kolar"
