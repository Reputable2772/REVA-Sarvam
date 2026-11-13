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

def test_process_universal_voice_command_kannada():
    """Verify STT understanding of harvest questionnaire parameters in Kannada"""
    res = client.post("/api/voice/process-command", json={
        "text": "ನಾನು ಇಂದು ಬೆಳಿಗ್ಗೆ 7 ಗಂಟೆಗೆ 250 ಕೆಜಿ ಟೊಮೆಟೊ ಕೊಯ್ಲು ಮಾಡಿದ್ದೇನೆ, ನಾಳೆ ಬೆಳಿಗ್ಗೆ ಮಾರಾಟ ಮಾಡಬೇಕು",
        "language_code": "kn-IN"
    })
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["action"] in ["UPDATE_HARVEST", "SUBMIT_HARVEST"]
    assert data["entities"]["crop"] == "Tomato"
    assert data["entities"]["quantity_kg"] == 250.0
    assert data["entities"]["harvest_date"] == "2026-11-13"
    assert data["entities"]["harvest_time"] == "07:00 AM"
    assert data["entities"]["sell_immediately"] is True
    assert len(data["audio_base64"]) > 0

def test_process_universal_voice_command_navigation():
    """Verify app-wide STT command understanding for navigation"""
    res = client.post("/api/voice/process-command", json={
        "text": "Show me live mandi market rates for Kolar",
        "language_code": "en-IN"
    })
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["action"] == "NAVIGATE_MARKETPLACE"

def test_harvest_registration_questionnaire_fields():
    """Verify farmer registration with explicit questionnaire parameters and unique harvest ID"""
    req = {
        "farmer_id": "FARMER-01",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 300.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "harvest_time": "06:30 AM",
        "expected_date_time": "2026-11-14 09:00 AM",
        "sell_immediately": True,
        "village": "Mulbagal"
    }
    res = client.post("/api/farmer/register", json=req)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "harvest_id" in data
    assert data["harvest_id"].startswith("FR-KLR-")
    assert data["lot"]["harvest_time"] == "06:30 AM"
    assert data["lot"]["sell_immediately"] is True

    # Check retrieval by unique harvest ID
    lot_res = client.get(f"/api/farmer/lot/{data['harvest_id']}")
    assert lot_res.status_code == 200
    lot_data = lot_res.json()
    assert lot_data["harvest_id"] == data["harvest_id"]
    assert lot_data["quantity_kg"] == 300.0
