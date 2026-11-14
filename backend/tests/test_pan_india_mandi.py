import pytest
from backend.app.services.pan_india_mandi_service import PanIndiaMandiService
from fastapi.testclient import TestClient
from backend.main import app as marketplace_app
from backend.app.main import app as unified_app

def test_pan_india_mandi_service_loading():
    service = PanIndiaMandiService.get_instance()
    states = service.get_all_states()
    assert len(states) >= 20
    assert "Karnataka" in states
    assert "Maharashtra" in states
    assert "Punjab" in states

def test_pan_india_mandi_districts():
    service = PanIndiaMandiService.get_instance()
    karnataka_districts = service.get_districts_by_state("Karnataka")
    assert "Kolar" in karnataka_districts
    assert len(karnataka_districts) >= 5

def test_pan_india_mandi_search():
    service = PanIndiaMandiService.get_instance()
    # Search Tomato in Karnataka
    results = service.search_mandis(commodity="Tomato", state="Karnataka")
    assert len(results) > 0
    markets = [r["market"] for r in results]
    assert any("Bangarpet" in m or "Malur" in m or "Chintamani" in m or "Srinivasapur" in m for m in markets)

def test_pan_india_mandi_netback_and_trap_detection():
    service = PanIndiaMandiService.get_instance()
    analysis = service.evaluate_pan_india_mandi_netbacks(
        commodity="Tomato",
        quantity_kg=800.0,
        origin_state="Karnataka",
        origin_district="Kolar",
        candidate_limit=10
    )
    assert analysis["commodity"] == "Tomato"
    assert analysis["quantity_kg"] == 800.0
    assert len(analysis["mandis"]) > 0

    first = analysis["mandis"][0]
    assert "market" in first
    assert "gross_revenue" in first
    assert "deductions" in first
    assert "transport" in first["deductions"]
    assert "commission" in first["deductions"]
    assert "unloading" in first["deductions"]
    assert "weighment" in first["deductions"]
    assert "net_take_home" in first
    assert "effective_rate_per_kg" in first

    # Confirm net take home = gross_revenue - total_deductions
    expected_net = round(first["gross_revenue"] - first["deductions"]["total"], 2)
    assert abs(first["net_take_home"] - expected_net) < 0.05

def test_pan_india_mandi_api_endpoints():
    client = TestClient(marketplace_app)
    
    # 1. States endpoint
    res_states = client.get("/api/market-data/pan-india/states")
    assert res_states.status_code == 200
    data_states = res_states.json()
    assert data_states["success"] is True
    assert "Karnataka" in data_states["states"]

    # 2. Search endpoint
    res_search = client.get("/api/market-data/pan-india/search?commodity=Tomato&state=Karnataka")
    assert res_search.status_code == 200
    data_search = res_search.json()
    assert data_search["success"] is True
    assert len(data_search["results"]) > 0

    # 3. Mandi analysis endpoint
    res_analysis = client.get("/api/market-data/pan-india/mandi-analysis?commodity=Tomato&quantity_kg=800")
    assert res_analysis.status_code == 200
    data_analysis = res_analysis.json()
    assert data_analysis["success"] is True
    assert data_analysis.get("all_distances_osrm_verified") is True
    assert "OSRM" in data_analysis.get("distance_verification", "")
    assert "analysis" in data_analysis
    assert len(data_analysis["analysis"]["mandis"]) > 0
    for item in data_analysis["analysis"]["mandis"]:
        assert item.get("is_osrm_verified") is True
        assert "OSRM" in item.get("routing_engine", "")
        assert item.get("duration_minutes", 0) > 0

def test_pan_india_mandi_evaluation_and_deductions():
    results = PanIndiaMandiService.evaluate_mandi_options(
        crop="Tomato",
        quantity_kg=200.0,
        farmer_district="Kolar",
        farmer_state="Karnataka"
    )
    assert len(results) >= 3
    for m in results:
        assert m.get("is_osrm_verified") is True
        assert "OSRM" in m.get("routing_engine", "")
        assert m.get("verification_badge") == "OSRM Verified ✓"
        assert m.get("duration_minutes", 0) > 0
        assert "transport_cost" in m["itemized_deductions"]
        assert "commission_cost" in m["itemized_deductions"]
        assert "unloading_cost" in m["itemized_deductions"]
        assert "weighing_fee" in m["itemized_deductions"]
        assert m["net_take_home"] == round(m["gross_revenue"] - m["itemized_deductions"]["total_deductions"], 2)

def test_pan_india_trap_detection():
    results = PanIndiaMandiService.evaluate_mandi_options(
        crop="Tomato",
        quantity_kg=200.0,
        farmer_district="Kolar",
        farmer_state="Karnataka"
    )
    recommended = next((m for m in results if m.get("is_recommended")), None)
    assert recommended is not None
    assert recommended["net_take_home"] >= results[-1]["net_take_home"]

def test_distance_service_osrm_verification():
    from backend.app.services.distance_service import DistanceService
    route = DistanceService.calculate_road_route_details("Kolar", "Bangalore")
    assert route["is_osrm_verified"] is True
    assert "OSRM" in route["routing_engine"]
    assert route["distance_km"] > 0
    assert route["duration_minutes"] > 0


