from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from datetime import datetime, timezone
from backend.app.database import get_db, Base, engine
from backend.app.models.models import Farmer, HarvestLot, BuyerRequirement, VirtualFpoPool, FarmerPoolShare, OperatorWeighment, EscrowOrder, AuditLog
from backend.app.seed_data import SEED_FARMERS, SEED_HARVEST_LOTS, SEED_BUYER_REQUIREMENTS
from backend.app.services.allocation_service import AllocationService
from backend.app.services.netback_service import NetbackService
from backend.app.config import settings

router = APIRouter(prefix="/demo", tags=["Demo Mode & Checkpoints"])

def ensure_demo_mode():
    if not settings.DEMO_MODE:
        raise HTTPException(status_code=403, detail="Demo endpoints are disabled in production mode.")

@router.post("/reset")
def reset_demo_scenario(db: Session = Depends(get_db)):
    ensure_demo_mode()

    # Recreate tables cleanly
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

    # 1. Seed 6 Farmers
    for f in SEED_FARMERS:
        db.add(Farmer(
            farmer_id=f["farmer_id"],
            name=f["name"],
            village=f["village"],
            district=f["district"],
            phone=f["phone"]
        ))

    # 2. Seed 6 Harvest Lots (200kg each = 1,200kg total)
    for l in SEED_HARVEST_LOTS:
        db.add(HarvestLot(
            lot_id=l["lot_id"],
            farmer_id=l["farmer_id"],
            crop=l["crop"],
            variety=l["variety"],
            quantity_kg=l["quantity_kg"],
            quality_grade=l["quality_grade"],
            harvest_date=l["harvest_date"],
            status=l["status"]
        ))

    # 3. Seed Buyer Requirements
    for b in SEED_BUYER_REQUIREMENTS:
        db.add(BuyerRequirement(
            id=b["id"],
            buyer_name=b["buyer_name"],
            buyer_type=b["buyer_type"],
            crop=b["crop"],
            variety=b["variety"],
            required_grade=b["required_grade"],
            target_quantity_kg=b["target_quantity_kg"],
            min_acceptable_kg=b["min_acceptable_kg"],
            max_price_per_kg=b["max_price_per_kg"],
            min_price_per_kg=b["min_price_per_kg"],
            destination_location=b["destination_location"],
            preferred_pickup_hub=b["preferred_pickup_hub"],
            payment_terms=b["payment_terms"],
            active=b["active"]
        ))

    # Audit log
    db.add(AuditLog(
        action="DEMO_RESET",
        actor_role="DEMO_CONTROLLER",
        actor_id="SYSTEM",
        details="Reset database to baseline: 6 smallholders (1,200kg) and Reliance 800kg requirement"
    ))
    db.commit()

    return {
        "success": True,
        "message": "Demo scenario reset successfully to baseline.",
        "farmers_seeded": len(SEED_FARMERS),
        "total_declared_kg": 1200.0,
        "primary_po_target_kg": 800.0,
        "demo_mode": True,
        "badge": "DEMO DATA"
    }

@router.post("/checkpoint/{checkpoint_id}")
def jump_to_checkpoint(checkpoint_id: int, db: Session = Depends(get_db)):
    ensure_demo_mode()
    if checkpoint_id not in [1, 2, 3, 4]:
        raise HTTPException(status_code=400, detail="Invalid checkpoint ID. Choose 1, 2, 3, or 4.")

    # Checkpoint 1: Base reset
    reset_demo_scenario(db)
    if checkpoint_id == 1:
        return {
            "checkpoint": 1,
            "title": "Supply Registered (6 Farmers / 1,200 kg)",
            "description": "6 smallholder farmers registered their lots in Mulbagal corridor with baseline netback comparisons.",
            "total_declared_kg": 1200.0,
            "status": "ready_for_pooling",
            "badge": "DEMO DATA"
        }

    # Checkpoint 2: Virtual FPO Proportional Allocation (800 kg Target)
    lots = db.query(HarvestLot).all()
    lot_dicts = [{"farmer_id": l.farmer_id, "lot_id": l.lot_id, "quantity_kg": l.quantity_kg} for l in lots]
    allocations = AllocationService.allocate_target(lot_dicts, target_kg=800.0)

    pool_id = "VFPO-KLR-20261113-001"
    pool = VirtualFpoPool(
        pool_id=pool_id,
        crop="Tomato",
        grade="Grade A",
        buyer_requirement_id="BUYER-REQ-001",
        target_quantity_kg=800.0,
        total_pooled_kg=800.0,
        contract_price_per_kg=28.50,
        total_freight_cost=3292.0,
        status="pending"
    )
    db.add(pool)

    # Total shared freight is 3292; allocate proportionally to quantity
    total_freight = 3292.0
    for a in allocations:
        freight_share = round((a["allocated_kg"] / 800.0) * total_freight, 2)
        gross = round(a["allocated_kg"] * 28.50, 2)
        net = round(gross - freight_share, 2)

        share = FarmerPoolShare(
            pool_id=pool_id,
            farmer_id=a["farmer_id"],
            lot_id=a["lot_id"],
            allocated_kg=a["allocated_kg"],
            share_pct=a["share_pct"],
            gross_amount=gross,
            allocated_freight=freight_share,
            net_payout=net,
            farmer_accepted=(checkpoint_id >= 3),
            buyer_accepted=(checkpoint_id >= 3)
        )
        db.add(share)

        # Update lot status
        lot_obj = db.query(HarvestLot).filter(HarvestLot.lot_id == a["lot_id"]).first()
        if lot_obj:
            lot_obj.status = "pooled"

    db.add(AuditLog(
        action="CHECKPOINT_2_APPLIED",
        actor_role="DEMO_CONTROLLER",
        actor_id="SYSTEM",
        details="Virtual FPO created: 800kg target allocated proportionally across 6 farmers (133.33kg each)"
    ))
    db.commit()

    if checkpoint_id == 2:
        return {
            "checkpoint": 2,
            "title": "Virtual FPO Formed & Proportional Allocation",
            "description": "Reliance Retail 800kg requirement matched. Exactly 133.33kg allocated per farmer using Largest-Remainder rounding.",
            "pool_id": pool_id,
            "total_allocated_kg": 800.0,
            "status": "pending_acceptance",
            "allocations": allocations,
            "badge": "DEMO DATA"
        }

    # Checkpoint 3: Mutual Acceptance & Logistics Dispatch
    pool.status = "dispatched"
    db.add(AuditLog(
        action="CHECKPOINT_3_APPLIED",
        actor_role="DEMO_CONTROLLER",
        actor_id="SYSTEM",
        details="Mutual acceptance confirmed. Tata Ace vehicle allocated for 800kg load with ₹549/farmer shared freight."
    ))
    db.commit()

    if checkpoint_id == 3:
        return {
            "checkpoint": 3,
            "title": "Mutual Acceptance & Route Dispatch",
            "description": "Farmers and buyer confirmed. Tata Ace dispatched for 144km Kolar corridor pickup.",
            "vehicle": "Tata Ace Gold (1.0 Ton)",
            "total_freight": 3292.0,
            "shared_freight_per_farmer": 548.67,
            "status": "in_transit",
            "badge": "DEMO DATA"
        }

    # Checkpoint 4: Operator Weighment & Instant Escrow Settlement
    # Simulate operator digital weighment for farmer 1 (Manjunath)
    lot1 = db.query(HarvestLot).filter(HarvestLot.farmer_id == "FARMER-01").first()
    if lot1:
        weighment = OperatorWeighment(
            lot_id=lot1.lot_id,
            operator_id="OP-KLR-01",
            declared_kg=133.33,
            actual_weighed_kg=130.0,
            grade_a_kg=115.0, # 88.5% Grade A
            grade_b_kg=12.0,  # 9.2% Grade B
            grade_c_kg=3.0,   # 2.3% Grade C
            notes="Field moisture 12%, minor sorting blemish on 12kg"
        )
        db.add(weighment)
        lot1.status = "weighed"

    # Simulated Escrow Settlement
    escrow = EscrowOrder(
        id="ESCROW-PO-800-01",
        pool_id=pool_id,
        buyer_id="BUYER-REQ-001",
        total_amount=22800.0, # 800kg * 28.50
        grade_a_amount=20000.0,
        grade_b_amount=2200.0,
        grade_c_amount=600.0,
        status="SETTLED",
        is_simulated=True,
        settled_at=datetime.now(timezone.utc)
    )
    db.add(escrow)
    pool.status = "settled"

    db.add(AuditLog(
        action="CHECKPOINT_4_APPLIED",
        actor_role="DEMO_CONTROLLER",
        actor_id="SYSTEM",
        details="Operator verified 130kg weighment. Grade A/B/C split applied. Escrow ₹22,800 settled via simulated instant UPI."
    ))
    db.commit()

    return {
        "checkpoint": 4,
        "title": "Weighment Verification & Instant Payout",
        "description": "Operator weighed 130kg actual, 3-way grade split applied, escrow settled directly via instant simulated UPI.",
        "escrow_id": "ESCROW-PO-800-01",
        "total_settled": 22800.0,
        "status": "SETTLED",
        "simulated": True,
        "badge": "SIMULATED PAYMENT"
    }

@router.get("/status")
def get_demo_status(db: Session = Depends(get_db)):
    ensure_demo_mode()
    farmers_count = db.query(Farmer).count()
    lots_count = db.query(HarvestLot).count()
    pools_count = db.query(VirtualFpoPool).count()
    escrows_count = db.query(EscrowOrder).count()

    return {
        "demo_mode": settings.DEMO_MODE,
        "farmers_count": farmers_count,
        "lots_count": lots_count,
        "pools_count": pools_count,
        "escrows_count": escrows_count,
        "badges": ["DEMO DATA", "SIMULATED PAYMENT"],
        "available_checkpoints": [1, 2, 3, 4]
    }
