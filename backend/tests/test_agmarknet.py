import pytest
import os
from fastapi.testclient import TestClient
from backend.main import app
from backend.agmarknet_service import AgmarknetImporter

client = TestClient(app)

def test_agmarknet_import_from_json():
    importer = AgmarknetImporter()
    mandis = importer.import_from_json()
    assert len(mandis) == 5
    mandi_names = [m["mandi_name"] for m in mandis]
    assert "Kolar APMC" in mandi_names
    assert "Bangarpet APMC" in mandi_names
    assert "Chintamani APMC" in mandi_names
    assert "Malur APMC" in mandi_names
    assert "Mulbagal Sub-Market" in mandi_names

    kolar = next(m for m in mandis if m["mandi_name"] == "Kolar APMC")
    assert kolar["modal_price_per_kg"] == 22.0
    assert kolar["distance_from_mulbagal_km"] == 30.0
    assert kolar["commission_pct"] == 6.5
    assert not kolar.get("is_trap", False)

    bangarpet = next(m for m in mandis if m["mandi_name"] == "Bangarpet APMC")
    assert bangarpet["modal_price_per_kg"] == 24.0
    assert bangarpet.get("is_trap", False) is True
    assert "Bangarpet Trap" in bangarpet.get("trap_reason", "")

def test_agmarknet_import_from_csv():
    importer = AgmarknetImporter()
    mandis = importer.import_from_csv()
    assert len(mandis) == 5
    bangarpet = next(m for m in mandis if m["mandi_name"] == "Bangarpet APMC")
    assert bangarpet["modal_price_per_kg"] == 24.0
    assert bangarpet["commission_pct"] == 8.0
    assert bangarpet["is_trap"] is True

def test_bangarpet_netback_trap_detection():
    importer = AgmarknetImporter()
    evaluations = importer.evaluate_mandi_netbacks(quantity_kg=100.0)
    assert len(evaluations) == 5

    kolar = next(e for e in evaluations if e["mandi_name"] == "Kolar APMC")
    bangarpet = next(e for e in evaluations if e["mandi_name"] == "Bangarpet APMC")

    # Gross revenue comparison
    assert bangarpet["gross_revenue"] > kolar["gross_revenue"]  # 2400.0 > 2200.0

    # Net take-home comparison (Kolar wins due to lower transport & commission deductions)
    assert kolar["net_take_home"] > bangarpet["net_take_home"]
    assert kolar["net_take_home"] == 1657.0
    assert bangarpet["net_take_home"] == 1606.0
    assert bangarpet["is_trap"] is True

    # The top ranked mandi is not Bangarpet
    assert evaluations[0]["mandi_name"] != "Bangarpet APMC"

def test_api_import_endpoints_and_evaluation():
    # Test JSON import endpoint
    res_json = client.post("/api/v1/market-data/import/json")
    assert res_json.status_code == 200
    data_json = res_json.json()
    assert data_json["status"] == "success"
    assert data_json["imported_count"] == 5

    # Test CSV import endpoint
    res_csv = client.post("/api/v1/market-data/import/csv")
    assert res_csv.status_code == 200
    data_csv = res_csv.json()
    assert data_csv["status"] == "success"
    assert data_csv["imported_count"] == 5

    # Test Mandi Netbacks query endpoint
    res_netbacks = client.get("/api/v1/market-data/mandi-netbacks?quantity_kg=100.0")
    assert res_netbacks.status_code == 200
    netbacks = res_netbacks.json()
    assert len(netbacks) == 5
    assert netbacks[0]["net_take_home"] >= netbacks[-1]["net_take_home"]
