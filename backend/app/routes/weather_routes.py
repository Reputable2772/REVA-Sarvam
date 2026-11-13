from fastapi import APIRouter, Query
from typing import Optional
from backend.app.services.weather_service import WeatherService

router = APIRouter(prefix="/weather", tags=["Agri Weather Intelligence"])

@router.get("")
def get_default_weather(location: Optional[str] = Query(None, description="City, district or village")):
    """
    Returns live weather condition and agricultural advisories for the given or regional location.
    """
    loc = location or ""
    return WeatherService.get_weather(location=loc)

@router.get("/{location}")
def get_weather_for_location(location: str):
    """
    Returns live weather condition and agricultural harvesting advisories via wttr.in.
    """
    return WeatherService.get_weather(location=location)
