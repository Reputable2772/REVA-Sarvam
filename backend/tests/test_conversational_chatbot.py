# -*- coding: utf-8 -*-
import pytest
from backend.app.services.sarvam_service import SarvamService

def test_no_tomato_default_on_greeting():
    """Verify that saying hello does not output 'Updated: 200kg Tomato'."""
    res_en = SarvamService.parse_universal_command("Hello assistant, who are you?", language_code="en-IN")
    assert res_en["action"] == "GREETING"
    assert "200kg Tomato" not in res_en["spoken_feedback"]
    assert "FarmRoute Saathi" in res_en["spoken_feedback"]

    res_kn = SarvamService.parse_universal_command("ನಮಸ್ಕಾರ, ನೀವು ಯಾರು?", language_code="kn-IN")
    assert res_kn["action"] == "GREETING"
    assert "ಟೊಮೆಟೊ" not in res_kn["spoken_feedback"]
    assert "ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ" in res_kn["spoken_feedback"]

def test_weather_without_tomato_default():
    """Verify weather query returns weather data without defaulting to Tomato."""
    res = SarvamService.parse_universal_command("What is the weather today?", language_code="en-IN")
    assert res["action"] == "NAVIGATE_WEATHER"
    assert "200kg Tomato" not in res["spoken_feedback"]
    assert "Weather" in res["spoken_feedback"]
    assert "rain probability" in res["spoken_feedback"]

def test_onion_rate_query_returns_onion_not_tomato():
    """Verify asking for onion rates evaluates Onion and not Tomato."""
    res = SarvamService.parse_universal_command("ಈರುಳ್ಳಿ ಮಾರುಕಟ್ಟೆ ದರ ಎಷ್ಟು?", language_code="kn-IN")
    assert res["action"] == "NAVIGATE_MARKETPLACE"
    assert "ಈರುಳ್ಳಿ" in res["spoken_feedback"] or "Onion" in res["spoken_feedback"]
    assert "ಟೊಮೆಟೊ" not in res["spoken_feedback"]

def test_general_mandi_rates_overview_without_crop():
    """Verify asking for general market rates without a crop gives overview and doesn't default to Tomato."""
    res = SarvamService.parse_universal_command("What are today's market rates?", language_code="en-IN")
    assert res["action"] == "NAVIGATE_MARKETPLACE"
    assert "Onion" in res["spoken_feedback"]
    assert "Which crop would you like a detailed net take-home calculation for?" in res["spoken_feedback"]

def test_agronomy_pest_disease_advisory():
    """Verify asking for pest or blight advice returns agronomy guidance."""
    res = SarvamService.parse_universal_command("How to control fungal blight disease in crops?", language_code="en-IN")
    assert res["action"] == "AGRONOMY_ADVISORY"
    assert "Mancozeb" in res["spoken_feedback"] or "drainage" in res["spoken_feedback"]

def test_multilingual_chatbot_in_telugu():
    """Verify asking in Telugu returns Telugu feedback."""
    res_te = SarvamService.parse_universal_command("నమస్కారం, సహాయం కావాలి", language_code="te-IN")
    assert res_te["action"] == "GREETING"
    assert res_te["language_code"] == "te-IN"
    assert "నమస్కారం" in res_te["spoken_feedback"]
    assert "ఫార్మ్‌రూట్" in res_te["spoken_feedback"]

def test_multilingual_chatbot_in_hindi():
    """Verify asking in Hindi returns Hindi feedback."""
    res_hi = SarvamService.parse_universal_command("नमस्ते, मौसम कैसा है?", language_code="hi-IN")
    assert res_hi["action"] == "NAVIGATE_WEATHER"
    assert res_hi["language_code"] == "hi-IN"
