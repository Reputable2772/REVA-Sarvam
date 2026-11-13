import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.sarvam_service import SarvamService

client = TestClient(app)

def test_sarvam_transcribe():
    res = client.post("/api/voice/transcribe", data={"language_code": "kn-IN"})
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "transcript" in data
    assert len(data["transcript"]) > 0

def test_sarvam_extract_english():
    text = "I have 750 kg of Grade A hybrid tomato in Mulbagal farm ready to harvest"
    res = client.post("/api/voice/extract", json={"text": text})
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    extracted = data["extracted"]
    assert extracted["crop"] == "Tomato"
    assert extracted["quantity_kg"] == 750.0
    assert extracted["quality_grade"] == "Grade A"
    assert extracted["village"] == "Mulbagal"

def test_sarvam_extract_kannada():
    text = "ನನ್ನ ಬಳಿ 600 ಕೆಜಿ ಗ್ರೇಡ್ ಬಿ ಟೊಮೆಟೊ ಇದೆ, ಬಂಗಾರಪೇಟೆ"
    res = client.post("/api/voice/extract", json={"text": text})
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    extracted = data["extracted"]
    assert extracted["crop"] == "Tomato"
    assert extracted["quantity_kg"] == 600.0
    assert extracted["quality_grade"] == "Grade B"
    assert extracted["village"] == "Bangarpet"

def test_sarvam_speak_kannada_and_english():
    # Kannada
    res_kn = client.post("/api/voice/speak", json={"text": "ನಮಸ್ಕಾರ, ನಿಮ್ಮ ಬೆಳೆ ನೋಂದಾಯಿಸಲಾಗಿದೆ", "language_code": "kn-IN"})
    assert res_kn.status_code == 200
    data_kn = res_kn.json()
    assert data_kn["success"] is True
    assert len(data_kn["audio_base64"]) > 0

    # English
    res_en = client.post("/api/voice/speak", json={"text": "Hello farmer, harvest registered", "language_code": "en-IN"})
    assert res_en.status_code == 200
    data_en = res_en.json()
    assert data_en["success"] is True
    assert len(data_en["audio_base64"]) > 0

def test_sarvam_voice_registration_pipeline():
    # Register harvest via full voice pipeline
    req = {
        "farmer_id": "FR-KLR-TEST-VOICE-01",
        "language_code": "kn-IN",
        "manual_text": "ನನ್ನ ಬಳಿ 800 ಕೆಜಿ ಗ್ರೇಡ್ ಎ ಟೊಮೆಟೊ ಇದೆ ಮುಳುಬಾಗಿಲು"
    }
    res = client.post("/api/voice/register-by-voice", json=req)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "extracted_entities" in data
    assert data["extracted_entities"]["quantity_kg"] == 800.0
    assert data["lot_registration"]["success"] is True
    assert data["lot_registration"]["lot_id"].startswith("FR-KLR-")
    assert "spoken_confirmation" in data
    assert len(data["spoken_confirmation"]["audio_base64"]) > 0
