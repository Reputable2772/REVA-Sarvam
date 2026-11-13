from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional
from .models import (
    BuyerRequirement,
    MatchResult,
    OfferNegotiation,
    NegotiationStatus,
    UpiPayoutRequest,
    UpiPayoutResponse
)
from .marketplace_service import MarketplaceService
from .escrow_service import EscrowService

app = FastAPI(
    title="FarmRoute AI - Marketplace & Escrow API",
    version="1.0.0",
    description="Institutional Marketplace matching and 7-stage Escrow settlement engine."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

marketplace = MarketplaceService()
escrow = EscrowService()

@app.get("/health")
def health():
    return {"status": "healthy", "service": "FastAPI Marketplace & Escrow"}

@app.get("/api/v1/marketplace/buyers", response_model=List[BuyerRequirement])
def list_buyers():
    return marketplace.list_buyers()

@app.post("/api/v1/marketplace/match", response_model=List[MatchResult])
def match_harvest_produce(crop: str, grade: str, quantity_kg: float):
    return marketplace.match_lot(crop=crop, grade=grade, quantity_kg=quantity_kg)

@app.post("/api/v1/marketplace/offers", response_model=OfferNegotiation, status_code=status.HTTP_201_CREATED)
def create_offer(
    buyer_id: str,
    lot_or_pool_id: str,
    is_aggregated: bool,
    crop: str,
    quantity_kg: float,
    price_per_kg: float
):
    return marketplace.create_offer(
        buyer_id=buyer_id,
        lot_or_pool_id=lot_or_pool_id,
        is_aggregated=is_aggregated,
        crop=crop,
        quantity_kg=quantity_kg,
        price_per_kg=price_per_kg
    )

@app.patch("/api/v1/marketplace/offers/{offer_id}/transition", response_model=OfferNegotiation)
def transition_offer_state(
    offer_id: str,
    new_status: NegotiationStatus,
    action_by: str,
    role: str,
    counter_price: Optional[float] = None,
    notes: Optional[str] = None
):
    try:
        return marketplace.transition_offer(
            offer_id=offer_id,
            new_status=new_status,
            action_by=action_by,
            role=role,
            counter_price=counter_price,
            notes=notes
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/api/v1/escrow/payout/instant", response_model=UpiPayoutResponse)
def execute_instant_payout(payout_req: UpiPayoutRequest):
    try:
        return escrow.execute_instant_upi_payout(payout_req)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
