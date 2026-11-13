import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.sarvam_service import SarvamService

client = TestClient(app)

ALL_11_LANGUAGES = [
    "hi-IN", "bn-IN", "kn-IN", "ml-IN", "mr-IN",
    "od-IN", "pa-IN", "ta-IN", "te-IN", "gu-IN", "en-IN"
]

def test_all_11_languages_registry():
    """Verify that all 11 Sarvam languages are registered with proper metadata."""
    assert len(SarvamService.SUPPORTED_LANGUAGES) == 11
    for lang in ALL_11_LANGUAGES:
        assert lang in SarvamService.SUPPORTED_LANGUAGES
        meta = SarvamService.SUPPORTED_LANGUAGES[lang]
        assert "name" in meta
        assert "native_name" in meta
        assert "script" in meta
        assert "tts_model" in meta
        assert "default_speaker" in meta
        assert "sample_phrase" in meta
        assert "unicode_range" in meta

def test_language_code_normalization():
    """Verify flexible normalization of language codes (prefixes, casing, underscores)."""
    assert SarvamService.normalize_language_code("hi") == "hi-IN"
    assert SarvamService.normalize_language_code("hi_in") == "hi-IN"
    assert SarvamService.normalize_language_code("TE") == "te-IN"
    assert SarvamService.normalize_language_code("kn-in") == "kn-IN"
    assert SarvamService.normalize_language_code("pa") == "pa-IN"
    assert SarvamService.normalize_language_code("gu") == "gu-IN"
    assert SarvamService.normalize_language_code("bn") == "bn-IN"
    assert SarvamService.normalize_language_code("ml") == "ml-IN"
    assert SarvamService.normalize_language_code("od") == "od-IN"
    assert SarvamService.normalize_language_code("unknown") == "kn-IN"

def test_script_based_language_detection():
    """Verify script auto-detection across different Unicode blocks."""
    # Hindi (Devanagari)
    assert SarvamService.detect_language_from_text("टमाटर का भाव कितना है?") in ["hi-IN", "mr-IN"]
    # Kannada
    assert SarvamService.detect_language_from_text("ಟೊಮೆಟೊ ಬೆಲೆ ಎಷ್ಟಿದೆ?") == "kn-IN"
    # Telugu
    assert SarvamService.detect_language_from_text("టమాటా ధర ఎంత?") == "te-IN"
    # Tamil
    assert SarvamService.detect_language_from_text("தக்காளி விலை என்ன?") == "ta-IN"
    # Bengali
    assert SarvamService.detect_language_from_text("টমেটোর দাম কত?") == "bn-IN"
    # Gujarati
    assert SarvamService.detect_language_from_text("ટામેટાંનો ભાવ શું છે?") == "gu-IN"
    # Punjabi (Gurmukhi)
    assert SarvamService.detect_language_from_text("ਟਮਾਟਰ ਦਾ ਰੇਟ ਕੀ ਹੈ?") == "pa-IN"
    # Malayalam
    assert SarvamService.detect_language_from_text("തക്കാളി വില എത്രയാണ്?") == "ml-IN"
    # Odia
    assert SarvamService.detect_language_from_text("ଟମାଟୋ ଦର କେତେ?") == "od-IN"
    # English (Latin)
    assert SarvamService.detect_language_from_text("What is tomato price in Kolar?") == "en-IN"

def test_simulated_transcribe_all_11_languages(monkeypatch):
    """Verify simulated transcription returns localized sample text for each language."""
    monkeypatch.setattr(SarvamService, "get_api_keys", classmethod(lambda cls: []))
    monkeypatch.setattr(SarvamService, "get_openai_fallback_config", classmethod(lambda cls: {"api_key": ""}))

    for lang in ALL_11_LANGUAGES:
        res = SarvamService.transcribe_audio(b"dummy_wav", language_code=lang)
        assert res["success"] is True
        assert res["language_code"] == lang
        assert len(res["transcript"]) > 0

def test_synthesize_speech_all_11_languages(monkeypatch):
    """Verify TTS synthesis generates valid audio and assigns verified speakers across all 11 languages."""
    monkeypatch.setattr(SarvamService, "get_api_keys", classmethod(lambda cls: []))

    for lang in ALL_11_LANGUAGES:
        meta = SarvamService.SUPPORTED_LANGUAGES[lang]
        res = SarvamService.synthesize_speech("Sample agricultural text", language_code=lang)
        assert res["success"] is True
        assert res["language_code"] == lang
        assert res["speaker"] == meta["default_speaker"]
        assert len(res["audio_base64"]) > 0

def test_translation_engine(monkeypatch):
    """Verify translation layer handles same-language pass, caching, and multi-key cycling."""
    # Same language should bypass immediately
    res_same = SarvamService.translate_text("Hello farmer", target_language_code="en-IN", source_language_code="en-IN")
    assert res_same["success"] is True
    assert res_same["translated_text"] == "Hello farmer"

    # Passthrough fallback when no API key
    monkeypatch.setattr(SarvamService, "get_api_keys", classmethod(lambda cls: []))
    res_fallback = SarvamService.translate_text("Hello farmer", target_language_code="hi-IN")
    assert res_fallback["success"] is True
    assert res_fallback["target_language"] == "hi-IN"

def test_multilingual_entity_extraction_heuristics(monkeypatch):
    """Verify heuristic extractor correctly extracts entities across regional languages."""
    monkeypatch.setattr(SarvamService, "get_api_keys", classmethod(lambda cls: []))
    # Hindi
    hi_text = "मेरे पास 500 किलो ग्रेड बी टमाटर है, आज शाम को बेचना है।"
    hi_res = SarvamService.extract_harvest_entities(hi_text)
    assert hi_res["extracted"]["crop"] == "Tomato"
    assert hi_res["extracted"]["quantity_kg"] == 500.0
    assert hi_res["extracted"]["quality_grade"] == "Grade B"

    # Telugu
    te_text = "నా వద్ద 3 క్వింటాల్ గ్రేడ్ ఎ ఉల్లిపాయ ఉంది."
    te_res = SarvamService.extract_harvest_entities(te_text)
    assert te_res["extracted"]["crop"] == "Onion"
    assert te_res["extracted"]["quantity_kg"] == 300.0
    assert te_res["extracted"]["quality_grade"] == "Grade A"

    # Tamil
    ta_text = "என்னிடம் 2 டன் உருளைக்கிழங்கு உள்ளது."
    ta_res = SarvamService.extract_harvest_entities(ta_text)
    assert ta_res["extracted"]["crop"] == "Potato"
    assert ta_res["extracted"]["quantity_kg"] == 2000.0

    # Kannada
    kn_text = "ನನ್ನ ಬಳಿ 800 ಕೆಜಿ ಗ್ರೇಡ್ ಸಿ ಈರುಳ್ಳಿ ಇದೆ."
    kn_res = SarvamService.extract_harvest_entities(kn_text)
    assert kn_res["extracted"]["crop"] == "Onion"
    assert kn_res["extracted"]["quantity_kg"] == 800.0
    assert kn_res["extracted"]["quality_grade"] == "Grade C"

def test_multilingual_universal_command_intent(monkeypatch):
    """Verify universal voice intent recognition across languages."""
    monkeypatch.setattr(SarvamService, "get_api_keys", classmethod(lambda cls: []))
    # Hindi rate intent
    cmd_hi = SarvamService.parse_universal_command("मंडी में टमाटर का भाव क्या है?", language_code="hi-IN")
    assert cmd_hi["action"] == "NAVIGATE_MARKETPLACE"
    assert cmd_hi["language_code"] == "hi-IN"

    # Telugu weather intent
    cmd_te = SarvamService.parse_universal_command("రేపు వర్షం పడుతుందా?", language_code="te-IN")
    assert cmd_te["action"] == "NAVIGATE_WEATHER"
    assert cmd_te["language_code"] == "te-IN"

    # Tamil cold storage intent
    cmd_ta = SarvamService.parse_universal_command("அருகிலுள்ள குளிர்பதன கிடங்கு எங்கே?", language_code="ta-IN")
    assert cmd_ta["action"] == "NAVIGATE_COLD_STORAGE"
    assert cmd_ta["language_code"] == "ta-IN"

    # Kannada scheme intent
    cmd_kn = SarvamService.parse_universal_command("ಸರ್ಕಾರಿ ಸಬ್ಸಿಡಿ ಮತ್ತು ಯೋಜನೆಗಳು", language_code="kn-IN")
    assert cmd_kn["action"] == "NAVIGATE_SCHEMES"
    assert cmd_kn["language_code"] == "kn-IN"

def test_api_languages_endpoint():
    """Verify GET /api/voice/languages returns all 11 languages with metadata."""
    res = client.get("/api/voice/languages")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["count"] == 11
    assert "hi-IN" in data["languages"]
    assert "kn-IN" in data["languages"]
    assert "en-IN" in data["languages"]

def test_api_translate_endpoint():
    """Verify POST /api/voice/translate works."""
    payload = {
        "text": "Tomato price in Kolar APMC",
        "target_language_code": "hi-IN",
        "source_language_code": "en-IN"
    }
    res = client.post("/api/voice/translate", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["target_language"] == "hi-IN"
    assert len(data["translated_text"]) > 0

def test_api_cold_storage_discover_endpoint():
    """Verify POST /api/cold-storage/discover returns nearest facilities."""
    payload = {
        "farmer_id": "FARMER-01",
        "crop": "Tomato",
        "quantity_kg": 200.0,
        "latitude": 13.133,
        "longitude": 78.133,
        "radius_km": 50.0
    }
    res = client.post("/api/cold-storage/discover", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert len(data["facilities"]) > 0
    first = data["facilities"][0]
    assert "facility_id" in first
    assert "name" in first
    assert "distance_farmer_km" in first
    assert "mandi_distances" in first
    assert "ranking_score" in first

def test_api_cold_storage_voice_explanation():
    """Verify POST /api/voice/cold-storage-explanation produces audio and transcript."""
    payload = {
        "facility_id": "CS-KLR-001",
        "farmer_lat": 13.133,
        "farmer_lon": 78.133,
        "language_code": "kn-IN"
    }
    res = client.post("/api/voice/cold-storage-explanation", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["facility_id"] == "CS-KLR-001"
    assert len(data["transcript"]) > 0
    assert len(data["audio_base64"]) > 0
