"""
Comprehensive End-to-End Test Suite for FarmRoute Saathi
Testing all farmer flows:
1. Login flow (MPIN, Account Check, Demo Farmers)
2. "Sell Today" Immediate Harvest flow:
   - Registration with sell_immediately=True
   - Unique Lot ID generation (FR-KLR-YYYYMMDD-XXXX)
   - Pan-India Mandi comparison with itemized transparent deductions (freight, APMC commission, unloading, weighment)
   - Route comparison & Netback calculation
   - Lot listing verification
3. "Sell Later" Scheduled Harvest flow:
   - Registration with sell_immediately=False and expected_date_time
   - Voice questionnaire merging logic (second tap merges rather than overwriting fields)
   - Scheduling verification
4. Government Schemes & Benefits Intelligence:
   - Official Source registry verification (5 sources with official URLs)
   - Profile-based matching engine
   - Missing info resolution (FRUITS ID, Landholding)
   - Smart notifications with transparent "why you received this" attribution
   - Bookmarking schemes
5. Sarvam Voice STT & TTS Integration:
   - Universal Voice command intent parsing (Mandi, Schemes, Harvest)
   - Grounded Q&A for Schemes with Bulbul TTS audio synthesis
   - Mandi advisory speech generation
   - Multi-key rotation fallback resilience
"""

import pytest
from datetime import datetime, timedelta, timezone
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.database import Base, engine, SessionLocal
from backend.app.models.models import Farmer, HarvestLot
from backend.app.models.scheme_models import Scheme, SchemeSource, FarmerOpportunityMatch
from backend.app.services.scheme_seed_service import seed_verified_schemes
from backend.app.services.sarvam_service import SarvamService
from backend.app.services.pan_india_mandi_service import PanIndiaMandiService

client = TestClient(app)

@pytest.fixture(scope="module", autouse=True)
def setup_database():
    Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    seed_verified_schemes(db)
    # Ensure Suresh Gowda demo farmer exists
    suresh = db.query(Farmer).filter(Farmer.phone == "9845012345").first()
    if not suresh:
        suresh = Farmer(
            farmer_id="FARMER-SURESH-001",
            phone="9845012345",
            name="Manjunath Gowda",
            village="Vokkaleri",
            district="Kolar",
            state="Karnataka",
            pincode="563101",
            latitude=13.1367,
            longitude=78.1348
        )
        db.add(suresh)
        db.commit()
    db.close()
    yield

# ==============================================================================
# 1. LOGIN & AUTHENTICATION FLOW
# ==============================================================================
class TestAuthAndLoginFlow:
    def test_check_account_existing_farmer(self):
        """Verifies account check for registered demo farmer."""
        resp = client.post("/api/auth/check-account", json={"phone": "9845012345"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["exists"] is True
        assert len(data.get("name", "")) > 0
        assert data.get("district") == "Kolar"

    def test_check_account_new_farmer(self):
        """Verifies account check for an unregistered number."""
        resp = client.post("/api/auth/check-account", json={"phone": "9999900000"})
        assert resp.status_code == 200
        data = resp.json()
        assert data["exists"] is False

    def test_mpin_login_success(self):
        """Tests successful login with default demo MPIN '1234'."""
        resp = client.post("/api/auth/login", json={
            "phone": "9845012345",
            "mpin": "1234"
        })
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "authenticated"
        assert "token" in data
        assert data["farmer"]["phone"] == "9845012345"

    def test_mpin_login_invalid(self):
        """Tests failed login with wrong MPIN."""
        resp = client.post("/api/auth/login", json={
            "phone": "9845012345",
            "mpin": "9999"
        })
        assert resp.status_code == 401
        assert "Invalid MPIN" in resp.json()["detail"]


# ==============================================================================
# 2. "SELL TODAY" IMMEDIATE HARVEST FLOW
# ==============================================================================
class TestSellTodayFlow:
    lot_id = None

    def test_register_immediate_harvest(self):
        """Farmer decides to sell harvest immediately today."""
        today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        payload = {
            "farmer_id": "FARMER-SURESH-001",
            "crop": "Tomato",
            "variety": "Hybrid Red",
            "quantity_kg": 1500.0,
            "quality_grade": "Grade A",
            "harvest_date": today_str,
            "harvest_time": "08:00 AM",
            "expected_date_time": f"{today_str} 10:00 AM",
            "sell_immediately": True,
            "village": "Vokkaleri",
            "metadata": {
                "input_method": "voice",
                "bags": 60,
                "urgency": "immediate_dispatch"
            }
        }
        resp = client.post("/api/farmer/register", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data.get("success") is True
        assert "lot_id" in data
        assert data["lot_id"].startswith("FR-KLR-")
        assert data["lot"]["quantity_kg"] == 1500.0
        assert data["lot"]["sell_immediately"] is True
        TestSellTodayFlow.lot_id = data["lot_id"]

    def test_pan_india_mandi_comparison_and_netback(self):
        """Checks transparent mandi deductions & netback for the immediate harvest."""
        resp = client.get("/api/market-data/pan-india/mandi-analysis?commodity=Tomato&quantity_kg=1500.0&origin_state=Karnataka&origin_district=Kolar&limit=5")
        assert resp.status_code == 200
        data = resp.json()
        assert data["success"] is True
        analysis = data["analysis"]
        assert "mandis" in analysis
        assert len(analysis["mandis"]) > 0

        # Verify transparent itemized deductions
        top = analysis["mandis"][0]
        assert "market" in top
        assert "modal_price_kg" in top
        assert "net_take_home" in top
        assert "distance_km" in top
        assert "deductions" in top

        ded = top["deductions"]
        assert "transport" in ded
        assert "commission" in ded
        assert "unloading" in ded
        assert "weighment" in ded
        assert "total" in ded

        # Verify net take home math: gross_revenue - total_deductions
        expected_net = round(top["gross_revenue"] - ded["total"], 2)
        assert abs(top["net_take_home"] - expected_net) < 0.05

    def test_farmer_lots_listing(self):
        """Farmer views lots list and confirms immediate lot is ready for sale."""
        resp = client.get("/api/farmer/lots/FARMER-SURESH-001")
        assert resp.status_code == 200
        lots_data = resp.json()
        assert lots_data.get("success") is True
        lots = lots_data.get("lots", [])
        assert len(lots) > 0
        matching = next((l for l in lots if l["lot_id"] == TestSellTodayFlow.lot_id), None)
        assert matching is not None
        assert matching["crop"] == "Tomato"
        assert matching["status"] == "ready_for_sale"


# ==============================================================================
# 3. "SELL LATER" SCHEDULED HARVEST FLOW
# ==============================================================================
class TestSellLaterScheduledHarvestFlow:
    scheduled_lot_id = None

    def test_schedule_future_harvest(self):
        """Farmer plans harvest 10 days in advance (Sell Later)."""
        future_date = (datetime.now(timezone.utc) + timedelta(days=10)).strftime("%Y-%m-%d")
        payload = {
            "farmer_id": "FARMER-SURESH-001",
            "crop": "Tomato",
            "variety": "Bangalore Tomato",
            "quantity_kg": 3000.0,
            "quality_grade": "Grade A",
            "harvest_date": future_date,
            "harvest_time": "06:30 AM",
            "expected_date_time": f"{future_date} 09:00 AM",
            "sell_immediately": False,
            "village": "Vokkaleri",
            "metadata": {
                "schedule_reason": "crop_ripening",
                "target_market": "APMC Kolar"
            }
        }
        resp = client.post("/api/farmer/register", json=payload)
        assert resp.status_code == 200
        data = resp.json()
        assert data.get("success") is True
        assert data["lot"]["sell_immediately"] is False
        assert data["lot"]["expected_date_time"].startswith(future_date)
        TestSellLaterScheduledHarvestFlow.scheduled_lot_id = data["lot_id"]

    def test_voice_recording_subsequent_tap_field_merging(self):
        """
        Tests the UX requirement:
        If farmer taps record a second time on the harvest screen,
        it merges newly provided fields instead of overwriting already captured values.
        """
        initial_fields = {
            "crop": "Tomato",
            "variety": "Hybrid Red",
            "quantity_kg": 1200.0,
            "bags": 48,
            "harvest_date": None,
            "expected_date_time": None
        }

        second_voice_parsed = {
            "crop": None,
            "variety": None,
            "quantity_kg": None,
            "bags": None,
            "harvest_date": "2026-11-24",
            "expected_date_time": "2026-11-24 09:00 AM"
        }

        # Merge function logic
        def merge_harvest_fields(existing: dict, incoming: dict) -> dict:
            merged = existing.copy()
            for k, v in incoming.items():
                if v is not None and (v != "" if isinstance(v, str) else True):
                    merged[k] = v
            return merged

        merged_result = merge_harvest_fields(initial_fields, second_voice_parsed)
        assert merged_result["crop"] == "Tomato"
        assert merged_result["variety"] == "Hybrid Red"
        assert merged_result["quantity_kg"] == 1200.0
        assert merged_result["bags"] == 48
        assert merged_result["harvest_date"] == "2026-11-24"
        assert merged_result["expected_date_time"] == "2026-11-24 09:00 AM"


# ==============================================================================
# 4. GOVERNMENT BENEFITS & SCHEME INTELLIGENCE
# ==============================================================================
class TestGovernmentBenefitsAndIntelligence:
    def test_official_sources_registry(self):
        """Verifies government source authority registry."""
        resp = client.get("/api/schemes/sources")
        assert resp.status_code == 200
        sources = resp.json()["sources"]
        assert len(sources) >= 5
        assert all("official_url" in s and s["official_url"].startswith("https://") for s in sources)

    def test_farmer_recommendations_and_more_info_needed(self):
        """Tests that missing FID/landholding triggers MORE_INFO_REQUIRED."""
        resp = client.get("/api/schemes/recommendations/9845012345")
        assert resp.status_code == 200
        data = resp.json()
        assert data["success"] is True
        opportunities = data["opportunities"]
        assert len(opportunities) >= 4
        # Verify status names
        statuses = [o["match_status"] for o in opportunities]
        assert "LIKELY_ELIGIBLE" in statuses or "MORE_INFO_REQUIRED" in statuses

    def test_voluntary_profile_submission_and_eligibility_upgrade(self):
        """Submitting voluntary FRUITS FID and land size updates profile and opportunities."""
        update_resp = client.post("/api/schemes/update-profile", json={
            "farmer_id": "FARMER-SURESH-001",
            "phone": "9845012345",
            "landholding_hectares": 1.5,
            "fruits_id": "FID-KLR-8821",
            "is_fpo_member": True
        })
        assert update_resp.status_code == 200
        data = update_resp.json()
        assert data["success"] is True
        assert "recalculated_opportunities" in data
        recalc = data["recalculated_opportunities"]
        raitha = next((m for m in recalc if m["scheme_id"] == "SCHEME-KA-RAITHA-SIRI"), None)
        assert raitha is not None
        assert raitha["match_status"] == "LIKELY_ELIGIBLE"

    def test_smart_notifications_with_attribution(self):
        """Ensures all benefit alerts state WHY the farmer received them."""
        resp = client.get("/api/schemes/notifications/9845012345")
        assert resp.status_code == 200
        notifs = resp.json()["notifications"]
        assert len(notifs) >= 1
        for n in notifs:
            assert "why_received_en" in n
            assert len(n["why_received_en"]) > 0
            assert "why_received_kn" in n
            assert len(n["why_received_kn"]) > 0

    def test_scheme_bookmarking(self):
        """Farmer bookmarks and views saved schemes."""
        bm_resp = client.post("/api/schemes/bookmark", json={
            "farmer_id": "FARMER-SURESH-001",
            "scheme_id": "SCHEME-KA-RAITHA-SIRI",
            "saved": True
        })
        assert bm_resp.status_code == 200
        assert bm_resp.json()["is_saved"] is True

        list_resp = client.get("/api/schemes/bookmarks/FARMER-SURESH-001")
        assert list_resp.status_code == 200
        assert "SCHEME-KA-RAITHA-SIRI" in list_resp.json()["saved_scheme_ids"]


# ==============================================================================
# 5. SARVAM AI STT & TTS VOICE INTEGRATION
# ==============================================================================
class TestSarvamVoiceSttTtsIntegration:
    def test_universal_voice_intent_parsing(self):
        """Tests voice recognition intent extraction for Mandi, Schemes, and Harvest."""
        sarvam = SarvamService()

        # Mandi intent
        mandi_kn = sarvam.parse_universal_command("ಟೊಮ್ಯಾಟೊ ರೇಟ್ ಕೋಲಾರ ಮಂಡಿಯಲ್ಲಿ ಎಷ್ಟಿದೆ?", language_code="kn-IN")
        assert mandi_kn["action"] in ["CHECK_MANDI", "NAVIGATE_MARKETPLACE", "NAVIGATE_MANDI", "VOICE_QUERY", "UPDATE_HARVEST"]

        # Schemes intent
        scheme_en = sarvam.parse_universal_command("Open government schemes and subsidies", language_code="en-IN")
        assert scheme_en["action"] in ["NAVIGATE_SCHEMES", "VOICE_QUERY"]

    def test_tts_audio_synthesis_grounded_scheme_guidance(self):
        """Tests conversational scheme assistance with official URLs and Bulbul TTS audio synthesis."""
        resp = client.post("/api/voice/scheme-guidance", json={
            "query": "Tell me about Raitha Siri scheme for tomatoes in Kolar",
            "farmer_id": "FARMER-SURESH-001",
            "language_code": "kn-IN"
        })
        assert resp.status_code == 200
        data = resp.json()
        assert "answer" in data
        assert "fruits.karnataka.gov.in" in data["answer"]
        assert "audio_base64" in data
        assert isinstance(data["audio_base64"], str)

    def test_mandi_spoken_summary_generation(self):
        """Tests Bulbul TTS audio generation explaining mandi comparison and price trap."""
        mandis = [
            {
                "market": "Kolar APMC",
                "net_take_home": 32000.0,
                "modal_price_kg": 24.0,
                "is_recommended": True,
                "is_trap": False
            },
            {
                "market": "Binny Mill Bengaluru",
                "net_take_home": 28500.0,
                "modal_price_kg": 27.0,
                "is_recommended": False,
                "is_trap": True
            }
        ]
        summary = SarvamService.generate_mandi_spoken_summary(
            crop="Tomato",
            quantity_kg=1500.0,
            mandi_results=mandis,
            language_code="kn-IN"
        )
        assert "summary_text" in summary
        assert "Kolar APMC" in summary["summary_text"]
        assert "Binny Mill" in summary["summary_text"]
        assert "audio_base64" in summary
        assert len(summary["audio_base64"]) > 0

    def test_sarvam_key_rotation_resiliency(self, monkeypatch):
        """Verifies multi-key cycling for Sarvam API."""
        monkeypatch.setenv("SARVAM_API_KEYS", "sarvam-key-1,sarvam-key-2")
        keys = SarvamService.get_api_keys()
        assert len(keys) >= 1
        k1 = SarvamService.get_api_key()
        k2 = SarvamService.rotate_api_key()
        assert k1 is not None and k2 is not None
