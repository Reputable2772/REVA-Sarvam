import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.database import SessionLocal
from backend.app.models.models import ColdStorageFacility, MandiPriceRecord
from backend.app.services.dataset_importer import DatasetImporter

client = TestClient(app)

def test_national_cold_storage_ingestion_and_discovery():
    """Verify that MoFPI & NHB national cold storages are ingested and discoverable."""
    db = SessionLocal()
    # Trigger ingestion
    result = DatasetImporter.import_cold_storage_facilities(db)
    assert result["success"] is True
    assert result["total_facilities"] >= 300
    db.close()

    # Query without filter (national search)
    res = client.get("/api/cold-storage/facilities?limit=50")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["count"] > 0
    assert len(data["facilities"]) > 0

    # Query by state filter (e.g., Maharashtra)
    res_mh = client.get("/api/cold-storage/facilities?state=Maharashtra&limit=50")
    assert res_mh.status_code == 200
    data_mh = res_mh.json()
    assert data_mh["success"] is True
    assert data_mh["count"] >= 1
    assert any("Maharashtra" in f["location"] for f in data_mh["facilities"])

    # Query by district filter (e.g., Kolar corridor default seed preservation)
    res_klr = client.get("/api/cold-storage/facilities?district=Kolar")
    assert res_klr.status_code == 200
    data_klr = res_klr.json()
    assert data_klr["success"] is True
    # Ensure verified seed facility CS-KLR-001 is preserved
    facility_ids = [f["facility_id"] for f in data_klr["facilities"]]
    assert "CS-KLR-001" in facility_ids

def test_national_agmarknet_mandi_prices_and_summary():
    """Verify full AGMARKNET price dataset coverage and summary reporting."""
    db = SessionLocal()
    # Ingest full AGMARKNET dataset
    res_import = DatasetImporter.import_all_agmarknet_prices(db)
    assert res_import["success"] is True
    assert res_import["total_records"] >= 10000
    db.close()

    # Query API summary endpoint
    sum_res = client.get("/api/data/summary")
    assert sum_res.status_code == 200
    sum_data = sum_res.json()
    assert sum_data["success"] is True
    summary = sum_data["summary"]
    assert summary["total_mandi_price_records"] >= 10000
    assert summary["total_cold_storage_facilities"] >= 300
    assert summary["total_verified_schemes"] >= 5
    assert len(summary["states_covered"]) >= 20

    # Query mandi prices with state filter
    prices_res = client.get("/api/data/mandi-prices?state=Punjab&limit=10")
    assert prices_res.status_code == 200
    prices_data = prices_res.json()
    assert prices_data["success"] is True
    assert prices_data["count"] > 0
