from fastapi import APIRouter, Query, HTTPException
from typing import Optional, List, Dict, Any
from backend.app.services.pan_india_mandi_service import PanIndiaMandiService

router = APIRouter(prefix="/api/market-data/pan-india", tags=["Pan-India Mandi Engine"])

@router.get("/states")
def get_pan_india_states():
    """Returns all 26+ states available in the national AGMARKNET dataset."""
    service = PanIndiaMandiService.get_instance()
    states = service.get_all_states()
    return {"success": True, "count": len(states), "states": states}

@router.get("/districts")
def get_pan_india_districts(state: str = Query(..., description="State name")):
    """Returns all districts in a specified state."""
    service = PanIndiaMandiService.get_instance()
    districts = service.get_districts_by_state(state)
    return {"success": True, "state": state, "count": len(districts), "districts": districts}

@router.get("/commodities")
def get_pan_india_commodities(state: Optional[str] = Query(None, description="Optional state filter")):
    """Returns commodities traded across India (or in a specific state)."""
    service = PanIndiaMandiService.get_instance()
    commodities = service.get_commodities(state)
    return {"success": True, "count": len(commodities), "commodities": commodities}

@router.get("/search")
def search_pan_india_mandis(
    commodity: Optional[str] = Query(None, description="Commodity name, e.g., Tomato, Onion"),
    state: Optional[str] = Query(None, description="State filter"),
    district: Optional[str] = Query(None, description="District filter"),
    query: Optional[str] = Query(None, description="Keyword search query"),
    limit: int = Query(50, ge=1, le=500, description="Max results")
):
    """
    Search any Mandi across India from the 13,073-record national AGMARKNET dataset.
    """
    service = PanIndiaMandiService.get_instance()
    results = service.search_mandis(commodity=commodity, state=state, district=district, query=query, limit=limit)
    return {
        "success": True,
        "count": len(results),
        "results": results
    }

@router.get("/mandi-analysis")
def analyze_pan_india_mandi_netbacks(
    commodity: str = Query("Tomato", description="Crop commodity"),
    quantity_kg: float = Query(800.0, ge=1.0, description="Harvest quantity in kg"),
    origin_state: str = Query("Karnataka", description="Farmer origin state"),
    origin_district: str = Query("Kolar", description="Farmer origin district"),
    limit: int = Query(10, ge=1, le=30, description="Number of mandis to compare")
):
    """
    Computes transparent itemized deductions (distance freight, APMC commission,
    unloading, weighment) and flags Headline Price Traps for any Mandi across India.
    """
    service = PanIndiaMandiService.get_instance()
    analysis = service.evaluate_pan_india_mandi_netbacks(
        commodity=commodity,
        quantity_kg=quantity_kg,
        origin_state=origin_state,
        origin_district=origin_district,
        candidate_limit=limit
    )
    return {
        "success": True,
        "analysis": analysis
    }
