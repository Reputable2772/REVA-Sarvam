from fastapi import APIRouter
from backend.app.services.weather_service import WeatherService

router = APIRouter(prefix="/weather", tags=["Agri Weather Intelligence"])

@router.get("/{location}")
def get_weather_for_location(location: str = "Kolar"):
    """
    Returns live weather condition and tomato harvesting advisories via wttr.in.
    """
    return WeatherService.get_weather(location=location)

@router.get("")
def get_default_weather():
    """
    Default weather for Kolar district baseline.
    """
    return WeatherService.get_weather(location="Kolar")
