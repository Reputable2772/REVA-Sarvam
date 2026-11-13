from pydantic import BaseModel, Field
from typing import List, Optional, Literal
from datetime import datetime

QualityGrade = Literal['Grade A', 'Grade B', 'Grade C', 'Any']
BuyerType = Literal['Supermarket', 'Hotel', 'Processor', 'Wholesaler']
NegotiationStatus = Literal['LISTED', 'MATCHED', 'OFFER_SENT', 'COUNTER_OFFER', 'ACCEPTED', 'REJECTED', 'FULFILLED']
EscrowStage = Literal['DECLARED', 'FIELD_CONFIRMED', 'COLLECTED', 'WEIGHED', 'DELIVERED', 'PAYMENT_DUE', 'PAID']

class BuyerRequirement(BaseModel):
    id: str
    buyer_name: str
    buyer_type: BuyerType
    crop: str
    variety: str = "Hybrid Red"
    required_grade: QualityGrade = "Grade A"
    target_quantity_kg: float
    min_acceptable_kg: Optional[float] = None
    max_price_per_kg: float
    pickup_hub: str = "Kolar Central Hub"
    payment_terms: str = "T+1 direct bank transfer"
    active: bool = True

class HarvestLot(BaseModel):
    lot_id: str
    farmer_id: str
    farmer_name: str
    village: str
    district: str = "Kolar"
    phone: Optional[str] = None
    crop: str
    variety: str
    quantity_kg: float
    quality_grade: QualityGrade
    status: str = "ready_for_sale"

class MatchScoreBreakdown(BaseModel):
    crop_match: bool
    grade_match: bool
    quantity_score: float
    price_score: float
    logistics_score: float
    total_score: float

class MatchResult(BaseModel):
    buyer_id: str
    buyer_name: str
    crop: str
    required_grade: str
    target_quantity_kg: float
    offered_price_per_kg: float
    matched_quantity_kg: float
    compatibility_score: float
    breakdown: MatchScoreBreakdown
    recommendation: str

class NegotiationHistoryEntry(BaseModel):
    by: str
    role: str
    action: str
    price_per_kg: Optional[float] = None
    quantity_kg: Optional[float] = None
    notes: Optional[str] = None
    timestamp: str

class OfferNegotiation(BaseModel):
    id: str
    buyer_requirement_id: str
    buyer_name: str
    lot_or_pool_id: str
    is_aggregated_pool: bool
    crop: str
    quantity_kg: float
    current_price_per_kg: float
    status: NegotiationStatus
    history: List[NegotiationHistoryEntry] = []
    created_at: str
    updated_at: str


