import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from backend.app.database import Base
from backend.app.models.models import Farmer, HarvestLot
from backend.app.models.scheme_models import Scheme, SchemeSource
from backend.app.services.scheme_seed_service import seed_verified_schemes
from backend.app.services.scheme_matching_service import SchemeMatchingService
from backend.app.services.sarvam_service import SarvamService

@pytest.fixture
def test_db():
    engine = create_engine("sqlite:///:memory:")
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()

    # Seed demo farmers
    f1 = Farmer(
        farmer_id="FARMER-01",
        phone="9845012345",
        name="Manjunatha Gowda",
        village="Mulbagal",
        district="Kolar",
        state="Karnataka",
        pincode="563131",
        metadata_json='{"landholding_hectares": 1.2, "fruits_id": "FID-KLR-8821", "is_fpo_member": true}'
    )
    f2 = Farmer(
        farmer_id="FARMER-02",
        phone="9845023456",
        name="Venkataswamy",
        village="Bangarpet",
        district="Kolar",
        state="Karnataka",
        pincode="563114",
        metadata_json='{"landholding_hectares": 3.5, "fruits_id": null, "is_fpo_member": false}'
    )
    db.add(f1)
    db.add(f2)

    lot1 = HarvestLot(
        lot_id="FR-KLR-00251",
        farmer_id="FARMER-01",
        crop="Tomato",
        variety="Hybrid Red",
        quantity_kg=200.0,
        quality_grade="Grade A",
        harvest_date="2026-11-13"
    )
    lot2 = HarvestLot(
        lot_id="FR-KLR-00252",
        farmer_id="FARMER-02",
        crop="Onion",
        variety="Nasik Red",
        quantity_kg=400.0,
        quality_grade="Grade B",
        harvest_date="2026-11-13"
    )
    db.add(lot1)
    db.add(lot2)
    db.commit()

    seed_verified_schemes(db)
    yield db
    db.close()

def test_scheme_sources_and_seeding(test_db):
    sources = test_db.query(SchemeSource).all()
    assert len(sources) >= 5
    for s in sources:
        assert s.official_url.startswith("http")
        assert len(s.portal_name) > 0

    schemes = test_db.query(Scheme).all()
    assert len(schemes) == 5
    # Verify categories present
    cats = {s.category for s in schemes}
    assert "DIRECT_INCOME_SUPPORT" in cats
    assert "HORTICULTURE_INCENTIVE" in cats
    assert "CROP_INSURANCE" in cats
    assert "FPO_INSTITUTIONAL" in cats
    assert "MARKET_ACCESS" in cats

def test_deterministic_farmer_matching(test_db):
    # Farmer 1: Tomato, 1.2 ha, FRUITS ID, FPO member
    matches_f1 = SchemeMatchingService.evaluate_farmer_opportunities("FARMER-01", test_db)
    assert len(matches_f1) == 5

    raitha_siri = next(m for m in matches_f1 if m["scheme_id"] == "SCHEME-KA-RAITHA-SIRI")
    assert raitha_siri["match_status"] == "LIKELY_ELIGIBLE"
    assert "Smallholder landholding ceiling satisfied" in str(raitha_siri["matched_rules"])

    fpo_grant = next(m for m in matches_f1 if m["scheme_id"] == "SCHEME-SFAC-10K-FPO")
    assert fpo_grant["match_status"] == "LIKELY_ELIGIBLE"
    assert "You are an FPO member" in fpo_grant["relevance_reason_en"]

def test_large_landholder_and_missing_info(test_db):
    # Farmer 2: 3.5 ha, no FRUITS ID, not FPO member
    matches_f2 = SchemeMatchingService.evaluate_farmer_opportunities("FARMER-02", test_db)

    raitha_siri = next(m for m in matches_f2 if m["scheme_id"] == "SCHEME-KA-RAITHA-SIRI")
    # Exceeds 2.0 ha ceiling
    assert raitha_siri["match_status"] == "INELIGIBLE"
    assert "Exceeds smallholder ceiling" in raitha_siri["relevance_reason_en"]

    fpo_grant = next(m for m in matches_f2 if m["scheme_id"] == "SCHEME-SFAC-10K-FPO")
    # Not an FPO member, so it is Potentially Relevant with institutional note
    assert fpo_grant["match_status"] == "POTENTIALLY_RELEVANT"
    assert "Paid to FPO company" in fpo_grant["relevance_reason_en"]

def test_voluntary_profile_update(test_db):
    # Start with a farmer having no metadata
    f3 = Farmer(
        farmer_id="FARMER-03",
        phone="9845034567",
        name="Anand Kumar",
        village="Kolar",
        district="Kolar",
        state="Karnataka",
        pincode="563101",
        metadata_json='{}'
    )
    test_db.add(f3)
    test_db.commit()

    initial = SchemeMatchingService.evaluate_farmer_opportunities("FARMER-03", test_db)
    raitha_siri_initial = next(m for m in initial if m["scheme_id"] == "SCHEME-KA-RAITHA-SIRI")
    assert raitha_siri_initial["match_status"] == "MORE_INFO_REQUIRED"
    assert "fruits_id" in raitha_siri_initial["missing_fields"]

    # Provide voluntary fields
    updated = SchemeMatchingService.evaluate_farmer_opportunities(
        "FARMER-03",
        test_db,
        voluntary_profile_override={"landholding_hectares": 1.5, "fruits_id": "FID-KLR-9912"}
    )
    raitha_siri_updated = next(m for m in updated if m["scheme_id"] == "SCHEME-KA-RAITHA-SIRI")
    assert raitha_siri_updated["match_status"] == "LIKELY_ELIGIBLE"

def test_smart_notifications_and_deduplication(test_db):
    notifs_1 = SchemeMatchingService.generate_smart_notifications_for_farmer("FARMER-01", test_db)
    assert len(notifs_1) >= 2
    # Verify reason why received is present
    for n in notifs_1:
        assert "Why you received this" in n["why_received_en"]
        assert n["source_url"].startswith("http")

    # Second run does not create duplicate entries
    count_before = len(notifs_1)
    notifs_2 = SchemeMatchingService.generate_smart_notifications_for_farmer("FARMER-01", test_db)
    assert len(notifs_2) == count_before

def test_ai_guidance_scheme_queries(test_db):
    # Kannada FPO query
    res_kn = SarvamService.answer_scheme_query("FPO ಯೋಜನೆ ಹೇಗೆ ಕೆಲಸ ಮಾಡುತ್ತದೆ?", farmer_id="FARMER-01", language_code="kn-IN", db=test_db)
    assert "FPO" in res_kn["answer"]
    assert "15 ಲಕ್ಷ" in res_kn["answer"]
    assert "sfacindia.com" in res_kn["answer"]

    # English Raitha Siri query
    res_en = SarvamService.answer_scheme_query("How much subsidy under Raitha Siri?", farmer_id="FARMER-01", language_code="en-IN", db=test_db)
    assert "10,000" in res_en["answer"]
    assert "FRUITS" in res_en["answer"]
