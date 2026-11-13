from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime, timezone

from backend.app.config import settings
from backend.app.database import engine, Base
from backend.app.routes.farmer_routes import router as farmer_router
from backend.app.routes.logistics_routes import router as logistics_router
from backend.app.routes.data_routes import router as data_router
from backend.app.routes.sarvam_routes import router as sarvam_router
from backend.app.routes.auth_routes import router as auth_router
from backend.app.routes.weather_routes import router as weather_router
from backend.app.routes.cold_storage_routes import router as cold_storage_router
from backend.app.routes.sync_routes import router as sync_router
from backend.app.routes.notification_routes import router as notification_router
from backend.app.routes.pan_india_mandi_routes import router as pan_india_mandi_router
from backend.app.routes.scheme_routes import router as scheme_router

# Initialize database schema
Base.metadata.create_all(bind=engine)
with engine.connect() as conn:
    try:
        from sqlalchemy import text
        cols = [r[1] for r in conn.execute(text("PRAGMA table_info(notification_records)")).fetchall()]
        if "metadata_json" not in cols:
            conn.execute(text("ALTER TABLE notification_records ADD COLUMN metadata_json TEXT DEFAULT '{}'"))
            conn.commit()
    except Exception:
        pass

# Ensure verified seeds & official datasets are loaded
from backend.app.database import SessionLocal
from backend.app.services.dataset_importer import DatasetImporter
from backend.app.services.scheme_seed_service import seed_verified_schemes
from backend.app.routes.cold_storage_routes import ensure_seeded_facilities
from backend.app.models.models import MandiPriceRecord

with SessionLocal() as init_db:
    try:
        ensure_seeded_facilities(init_db)
        seed_verified_schemes(init_db)
        if init_db.query(MandiPriceRecord).count() < 1000:
            DatasetImporter.import_all_agmarknet_prices(init_db)
    except Exception as init_err:
        print(f"Warning during startup dataset seeding: {init_err}")


app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Farmer-first agricultural decision support, Virtual FPO aggregation, and smart logistics platform"
)

# Enable CORS for mobile and web access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "FarmRoute AI FastAPI Unified Backend",
        "version": settings.VERSION,
        "demo_mode": settings.DEMO_MODE,
        "database": "SQLite (WAL mode)",
        "modules": [
            "farmer", "logistics", "data_importer",
            "sarvam_voice_ai", "auth_mobile_mpin", "weather_wttr_in",
            "cold_storage_phase14", "offline_sync_phase15", "fcm_notifications_phase17",
            "schemes_intelligence", "pan_india_mandi"
        ],
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

# Mount subrouters under /api
app.include_router(farmer_router, prefix=settings.API_V1_PREFIX)
app.include_router(logistics_router, prefix=settings.API_V1_PREFIX)
app.include_router(data_router, prefix=settings.API_V1_PREFIX)
app.include_router(sarvam_router, prefix=settings.API_V1_PREFIX)
app.include_router(auth_router, prefix=settings.API_V1_PREFIX)
app.include_router(weather_router, prefix=settings.API_V1_PREFIX)
app.include_router(cold_storage_router, prefix=settings.API_V1_PREFIX)
app.include_router(sync_router, prefix=settings.API_V1_PREFIX)
app.include_router(notification_router, prefix=settings.API_V1_PREFIX)
app.include_router(scheme_router, prefix=settings.API_V1_PREFIX)
app.include_router(pan_india_mandi_router)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
