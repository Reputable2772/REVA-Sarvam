import pytest
from backend.marketplace_service import MarketplaceService
from backend.escrow_service import EscrowService

def test_marketplace_matching_and_recommendation():
    service = MarketplaceService()
    
    # 100kg tomato lot
    single_matches = service.match_lot(crop="Tomato", grade="Grade A", quantity_kg=100.0)
    assert len(single_matches) >= 2
    top = single_matches[0]
    assert "Virtual FPO Aggregation recommended" in top.recommendation
    
    # 600kg pooled lot
    pooled_matches = service.match_lot(crop="Tomato", grade="Grade A", quantity_kg=600.0)
    reliance = next(m for m in pooled_matches if "Reliance" in m.buyer_name)
    assert reliance.breakdown.quantity_score == 40.0
    assert reliance.compatibility_score >= 90.0

def test_negotiation_lifecycle_state_machine():
    service = MarketplaceService()
    
    # 1. Create offer
    offer = service.create_offer(
        buyer_id="BUYER-REQ-001",
        lot_or_pool_id="VFPO-POOL-001",
        is_aggregated=True,
        crop="Tomato",
        quantity_kg=600.0,
        price_per_kg=28.0,
        created_by="FPO Coordinator"
    )
    assert offer.status == "OFFER_SENT"
    assert offer.current_price_per_kg == 28.0
    
    # 2. Counter offer
    counter = service.transition_offer(
        offer_id=offer.id,
        new_status="COUNTER_OFFER",
        action_by="Reliance Buyer",
        role="buyer",
        counter_price=27.5,
        notes="Countering at 27.5 for morning pickup"
    )
    assert counter.status == "COUNTER_OFFER"
    assert counter.current_price_per_kg == 27.5

    # 3. Acceptance
    accepted = service.transition_offer(
        offer_id=offer.id,
        new_status="ACCEPTED",
        action_by="Farmer Lead",
        role="farmer",
        notes="Accepted counter rate"
    )
    assert accepted.status == "ACCEPTED"

    # 4. Fulfillment
    fulfilled = service.transition_offer(
        offer_id=offer.id,
        new_status="FULFILLED",
        action_by="Logistics Assurance",
        role="platform",
        notes="Order fulfilled at Whitefield DC"
    )
    assert fulfilled.status == "FULFILLED"
    assert len(fulfilled.history) == 4

def test_invalid_negotiation_transition():
    service = MarketplaceService()
    offer = service.create_offer(
        buyer_id="BUYER-REQ-001",
        lot_or_pool_id="LOT-001",
        is_aggregated=False,
        crop="Tomato",
        quantity_kg=100.0,
        price_per_kg=28.0
    )
    
    # Cannot jump directly from OFFER_SENT to FULFILLED
    with pytest.raises(ValueError, match="Invalid state transition"):
        service.transition_offer(
            offer_id=offer.id,
            new_status="FULFILLED",
            action_by="Farmer",
            role="farmer"
        )


