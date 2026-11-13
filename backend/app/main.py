from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime, timezone

from backend.app.config import settings
from backend.app.database import engine, Base
from backend.app.routes.farmer_routes import router as farmer_router
from backend.app.routes.logistics_routes import router as logistics_router
from backend.app.routes.data_routes import router as data_router
from backend.app.routes.demo_routes import router as demo_router

# Initialize database schema
Base.metadata.create_all(bind=engine)

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
        "modules": ["farmer", "logistics", "data_importer", "demo_checkpoints"],
        "timestamp": datetime.now(timezone.utc).isoformat()
    }

# Mount subrouters under /api
app.include_router(farmer_router, prefix=settings.API_V1_PREFIX)
app.include_router(logistics_router, prefix=settings.API_V1_PREFIX)
app.include_router(data_router, prefix=settings.API_V1_PREFIX)
app.include_router(demo_router, prefix=settings.API_V1_PREFIX)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.app.main:app", host="0.0.0.0", port=8000, reload=True)
