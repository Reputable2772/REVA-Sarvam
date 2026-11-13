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
    assert "analysis" in data_analysis
    assert len(data_analysis["analysis"]["mandis"]) > 0
