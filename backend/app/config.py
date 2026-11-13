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
    DEMO_MODE: bool = True

    class Config:
        env_file = ".env"

settings = Settings()
