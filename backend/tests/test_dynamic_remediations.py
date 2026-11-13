import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.database import SessionLocal
from backend.app.services.distance_service import DistanceService
from backend.app.services.netback_service import NetbackService
from backend.app.services.weather_protection_service import WeatherProtectionService
from backend.app.services.scheme_matching_service import SchemeMatchingService
from backend.app.models.models import Farmer, HarvestLot

client = TestClient(app)

def test_distance_service_resolve_nearby_mandis():
    db = SessionLocal()
    try:
        # Test 1: Kolar corridor
        m_dist, f_dist = DistanceService.resolve_nearby_mandis(
            district="Kolar",
            location="Kolar, Karnataka",
            lat=13.1367,
            lon=78.1291,
            db=db,
            farmer_lat=13.133,
            farmer_lon=78.133,
            limit=3
        )
        assert len(m_dist) >= 1
        assert len(f_dist) >= 1
        assert any("Kolar" in m["mandi_name"] or "Bangarpet" in m["mandi_name"] for m in m_dist)

        # Test 2: Other state (e.g. Nashik, Maharashtra)
        m_nashik, f_nashik = DistanceService.resolve_nearby_mandis(
            district="Nashik",
            location="Nashik, Maharashtra",
            lat=19.9975,
            lon=73.7898,
            db=db,
            farmer_lat=19.95,
            farmer_lon=73.75,
            limit=3
        )
        assert len(m_nashik) >= 1
        # Should NOT be hardcoded to Kolar APMC
        assert not any("Kolar APMC" == m["mandi_name"] for m in m_nashik)
    finally:
        db.close()

def test_cold_storage_discover_dynamic_mandis():
    # Discover in Kolar region
    res = client.post("/api/cold-storage/discover", json={
        "farmer_id": "FARMER-01",
        "latitude": 13.133,
        "longitude": 78.133,
        "radius_km": 50.0
    })
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert len(data["facilities"]) > 0
    fac = data["facilities"][0]
    assert "mandi_distances" in fac
    assert len(fac["mandi_distances"]) > 0
    # Recommendation reason should reference dynamic top mandi
    assert "recommendation_reason" in fac
    top_mandi = fac["mandi_distances"][0]["mandi_name"]
    assert top_mandi in fac["recommendation_reason"]

def test_logistics_dynamic_route_plan_benchmark():
    # Route with 3 distinct villages
    res = client.post("/api/logistics/route-plan", json={
        "villages": ["Mulbagal", "Bangarpet", "Malur"],
        "destination": "Bengaluru DC, Whitefield",
        "total_payload_kg": 900.0
    })
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "individual_transport_benchmark" in data
    assert "farmer_logistics_savings" in data
    # Benchmark should be a positive dynamically computed number
    benchmark = data["individual_transport_benchmark"]
    assert isinstance(benchmark, float) and benchmark > 0
    assert data["farmer_logistics_savings"] >= 0

def test_netback_dynamic_modal_rates():
    # Compute netback without hardcoded rate override
    res = NetbackService.calculate_comparison(
        quantity_kg=500.0,
        crop="Tomato",
        origin_district="Kolar",
        origin_state="Karnataka"
    )
    assert "channels" in res
    channels = {c["id"]: c for c in res["channels"]}
    assert "local_mandi" in channels
    assert "distant_mandi" in channels
    assert "virtual_fpo_direct" in channels
    assert channels["local_mandi"]["headline_rate_per_kg"] > 0
    assert channels["distant_mandi"]["headline_rate_per_kg"] > channels["local_mandi"]["headline_rate_per_kg"]
    assert channels["virtual_fpo_direct"]["headline_rate_per_kg"] > channels["local_mandi"]["headline_rate_per_kg"]

def test_scheme_matching_localized_explanation():
    db = SessionLocal()
    try:
        farmer = db.query(Farmer).first()
        if farmer:
            matches_hi = SchemeMatchingService.evaluate_farmer_opportunities(
                farmer_id=farmer.farmer_id,
                db=db,
                language_code="hi-IN"
            )
            assert len(matches_hi) > 0
            assert "relevance_reason_localized" in matches_hi[0]
            assert matches_hi[0]["language_code"] == "hi-IN"
    finally:
        db.close()
