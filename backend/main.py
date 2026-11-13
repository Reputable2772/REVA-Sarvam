from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from .agmarknet_service import AgmarknetImporter
from backend.app.routes.pan_india_mandi_routes import router as pan_india_mandi_router

app = FastAPI(
    title="FarmRoute AI - Market Data & Ingestion API",
    version="1.0.0",
    description="AGMARKNET flexible ingestion engine and Pan-India mandi routing."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

agmarknet = AgmarknetImporter()

@app.get("/health")
def health():
    return {"status": "healthy", "service": "FastAPI Market Data"}

@app.post("/api/v1/market-data/import/json")
def import_market_data_json():
    try:
        data = agmarknet.import_from_json()
        return {"status": "success", "imported_count": len(data), "mandis": [m["mandi_name"] for m in data]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/v1/market-data/import/csv")
def import_market_data_csv():
    try:
        data = agmarknet.import_from_csv()
        return {"status": "success", "imported_count": len(data), "mandis": [m["mandi_name"] for m in data]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/market-data/mandi-netbacks")
def get_mandi_netback_evaluations(quantity_kg: float = 100.0):
    return agmarknet.evaluate_mandi_netbacks(quantity_kg=quantity_kg)

app.include_router(pan_india_mandi_router)
