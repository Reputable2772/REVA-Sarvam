from typing import List, Optional
from datetime import datetime, timezone
from .models import (
    BuyerRequirement,
    HarvestLot,
    MatchResult,
    MatchScoreBreakdown,
    OfferNegotiation,
    NegotiationStatus,
    NegotiationHistoryEntry
)

class MarketplaceService:
    def __init__(self):
        self.buyers: List[BuyerRequirement] = [
            BuyerRequirement(
                id="BUYER-REQ-001",
                buyer_name="Reliance Retail (Bengaluru Hub)",
                buyer_type="Supermarket",
                crop="Tomato",
                variety="Hybrid Red",
                required_grade="Grade A",
                target_quantity_kg=600.0,
                min_acceptable_kg=500.0,
                max_price_per_kg=28.0,
                pickup_hub="Kolar Central Hub",
                payment_terms="T+1 direct escrow release"
            ),
            BuyerRequirement(
                id="BUYER-REQ-002",
                buyer_name="BigBasket Fresh (Hosakote Hub)",
                buyer_type="Supermarket",
                crop="Tomato",
                variety="Hybrid Red",
                required_grade="Grade A",
                target_quantity_kg=1000.0,
                min_acceptable_kg=800.0,
                max_price_per_kg=27.5,
                pickup_hub="Kolar Central Hub",
                payment_terms="T+2 escrow clearance"
            ),
            BuyerRequirement(
                id="BUYER-REQ-003",
                buyer_name="Nandi Food Processing Ltd",
                buyer_type="Processor",
                crop="Tomato",
                variety="Any",
                required_grade="Grade C",
                target_quantity_kg=2500.0,
                min_acceptable_kg=1500.0,
                max_price_per_kg=18.0,
                pickup_hub="Mulbagal APMC",
                payment_terms="Immediate weighment clearance"
            )
        ]
        self.offers: dict[str, OfferNegotiation] = {}
        self.counter = 1

    def list_buyers(self) -> List[BuyerRequirement]:
        return self.buyers

    def match_lot(self, crop: str, grade: str, quantity_kg: float) -> List[MatchResult]:
        matches: List[MatchResult] = []
        for buyer in self.buyers:
            if buyer.crop.lower() != crop.lower():
                continue
            
            # Grade compatibility
            grade_compatible = (
                buyer.required_grade == "Any" or 
                buyer.required_grade == grade or
                (buyer.required_grade == "Grade B" and grade == "Grade A") or
                (buyer.required_grade == "Grade C" and grade in ["Grade A", "Grade B"])
            )
            if not grade_compatible:
                continue
            
            grade_score = 30.0 if buyer.required_grade == grade else 25.0
            
            # Quantity scoring
            target = buyer.target_quantity_kg
            min_kg = buyer.min_acceptable_kg or target * 0.8
            if quantity_kg >= target:
                qty_score = 40.0
            elif quantity_kg >= min_kg:
                qty_score = 32.0
            else:
                qty_score = min(20.0, round((quantity_kg / target) * 20.0, 1))
            
            price_score = min(30.0, round((buyer.max_price_per_kg / 30.0) * 30.0, 1))
            # Dynamic logistics scoring based on calculated road distance
            from backend.app.services.distance_service import DistanceService
            hub_dist = DistanceService.calculate_road_distance(
                origin="Local Hub",
                destination=buyer.pickup_hub or "Local APMC"
            )
            if hub_dist <= 25.0:
                logistics_score = 30.0
            elif hub_dist <= 50.0:
                logistics_score = 25.0
            else:
                logistics_score = max(10.0, round(30.0 - (hub_dist / 10.0), 1))
            total = grade_score + qty_score + price_score + logistics_score

            recommendation = (
                f"Virtual FPO Aggregation recommended to fulfill {target}kg target"
                if quantity_kg < min_kg else
                f"Direct contract available with {buyer.buyer_name}"
            )

            matches.append(MatchResult(
                buyer_id=buyer.id,
                buyer_name=buyer.buyer_name,
                crop=buyer.crop,
                required_grade=buyer.required_grade,
                target_quantity_kg=buyer.target_quantity_kg,
                offered_price_per_kg=buyer.max_price_per_kg,
                matched_quantity_kg=min(quantity_kg, target),
                compatibility_score=round(total, 1),
                breakdown=MatchScoreBreakdown(
                    crop_match=True,
                    grade_match=True,
                    quantity_score=qty_score,
                    price_score=price_score,
                    logistics_score=logistics_score,
                    total_score=round(total, 1)
                ),
                recommendation=recommendation
            ))

        matches.sort(key=lambda m: m.compatibility_score, reverse=True)
        return matches

    def create_offer(
        self,
        buyer_id: str,
        lot_or_pool_id: str,
        is_aggregated: bool,
        crop: str,
        quantity_kg: float,
        price_per_kg: float,
        created_by: str = "FarmRoute Platform"
    ) -> OfferNegotiation:
        buyer = next((b for b in self.buyers if b.id == buyer_id), None)
        buyer_name = buyer.buyer_name if buyer else "Institutional Buyer"
        
        offer_id = f"OFFER-PY-{self.counter:04d}"
        self.counter += 1
        now = datetime.now(timezone.utc).isoformat()

        offer = OfferNegotiation(
            id=offer_id,
            buyer_requirement_id=buyer_id,
            buyer_name=buyer_name,
            lot_or_pool_id=lot_or_pool_id,
            is_aggregated_pool=is_aggregated,
            crop=crop,
            quantity_kg=quantity_kg,
            current_price_per_kg=price_per_kg,
            status="OFFER_SENT",
            history=[
                NegotiationHistoryEntry(
                    by=created_by,
                    role="platform" if is_aggregated else "farmer",
                    action="CREATED_OFFER",
                    price_per_kg=price_per_kg,
                    quantity_kg=quantity_kg,
                    notes="Direct institutional offer sent",
                    timestamp=now
                )
            ],
            created_at=now,
            updated_at=now
        )
        self.offers[offer_id] = offer
        return offer

    def transition_offer(
        self,
        offer_id: str,
        new_status: NegotiationStatus,
        action_by: str,
        role: str,
        counter_price: Optional[float] = None,
        notes: Optional[str] = None
    ) -> OfferNegotiation:
        if offer_id not in self.offers:
            raise ValueError(f"Offer {offer_id} not found.")

        offer = self.offers[offer_id]
        current = offer.status

        allowed = {
            'LISTED': ['MATCHED', 'OFFER_SENT'],
            'MATCHED': ['OFFER_SENT', 'REJECTED'],
            'OFFER_SENT': ['COUNTER_OFFER', 'ACCEPTED', 'REJECTED'],
            'COUNTER_OFFER': ['COUNTER_OFFER', 'ACCEPTED', 'REJECTED'],
            'ACCEPTED': ['FULFILLED'],
            'REJECTED': [],
            'FULFILLED': []
        }

        if new_status not in allowed.get(current, []):
            raise ValueError(f"Invalid state transition from {current} to {new_status}")

        now = datetime.now(timezone.utc).isoformat()
        if counter_price is not None:
            offer.current_price_per_kg = counter_price

        offer.status = new_status
        offer.updated_at = now
        offer.history.append(NegotiationHistoryEntry(
            by=action_by,
            role=role,
            action=f"TRANSITION_TO_{new_status}",
            price_per_kg=offer.current_price_per_kg,
            notes=notes,
            timestamp=now
        ))

        return offer
