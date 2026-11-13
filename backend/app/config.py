import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "FarmRoute AI"
    VERSION: str = "2.0.0"
    API_V1_PREFIX: str = "/api"
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./farmroute.db")
    SARVAM_API_KEY: str = os.getenv("SARVAM_API_KEY", "")
    DEMO_MODE: bool = True

    class Config:
        env_file = ".env"

settings = Settings()
