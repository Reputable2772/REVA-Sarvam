import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "FarmRoute AI"
    VERSION: str = "2.0.0"
    API_V1_PREFIX: str = "/api"
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./farmroute.db")
    SARVAM_API_KEY: str = os.getenv("SARVAM_API_KEY", "")
    SARVAM_API_KEYS: str = os.getenv("SARVAM_API_KEYS", "")  # Comma-separated keys
    OPENAI_COMPATIBLE_API_KEY: str = os.getenv("OPENAI_COMPATIBLE_API_KEY", "")
    OPENAI_COMPATIBLE_BASE_URL: str = os.getenv("OPENAI_COMPATIBLE_BASE_URL", "https://api.openai.com/v1")
    OPENAI_COMPATIBLE_MODEL: str = os.getenv("OPENAI_COMPATIBLE_MODEL", "gpt-3.5-turbo")
    FIREBASE_CREDENTIALS_PATH: str = os.getenv("FIREBASE_CREDENTIALS_PATH", "./serviceAccountKey.json")
    FIREBASE_PROJECT_ID: str = os.getenv("FIREBASE_PROJECT_ID", "")
    # OpenStreetMap / OSRM Road Distance & Routing Service
    OSRM_ROUTING_URL: str = os.getenv("OSRM_ROUTING_URL", "http://router.project-osrm.org/route/v1/driving")
    NOMINATIM_GEOCODING_URL: str = os.getenv("NOMINATIM_GEOCODING_URL", "https://nominatim.openstreetmap.org/search")
    OSM_USER_AGENT: str = os.getenv("OSM_USER_AGENT", "FarmRouteAI-Logistics/1.0")
    ENABLE_OSM_ROUTING: bool = os.getenv("ENABLE_OSM_ROUTING", "True").lower() in ("true", "1", "yes")
    DEMO_MODE: bool = True

    class Config:
        env_file = ".env"

settings = Settings()
