import json
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List, Tuple
from sqlalchemy.orm import Session

from backend.app.models.models import (
    Farmer,
    HarvestLot,
    PoolingOpportunity,
    NotificationRecord,
    AuditLog
)
from backend.app.services.distance_service import DistanceService

FLEET_TIERS = [
    {
        "id": "VEH-ACE",
        "name": "Tata Ace (Chhota Hathi)",
        "max_payload_kg": 1000,
        "base_charge": 500.0,
        "per_km_rate": 18.0,
        "handling_cost": 250.0,
    },
    {
        "id": "VEH-BOLERO",
        "name": "Mahindra Bolero Maxi Truck",
        "max_payload_kg": 1500,
        "base_charge": 750.0,
        "per_km_rate": 22.0,
        "handling_cost": 350.0,
    },
    {
        "id": "VEH-407",
        "name": "Tata 407 Light Truck",
        "max_payload_kg": 2500,
        "base_charge": 1200.0,
        "per_km_rate": 28.0,
        "handling_cost": 500.0,
    }
]

class HarvestAlignmentService:
    """
    Opportunistic Co-Transport Pooling & Bilateral Consent Engine.
    Coordinates between farmers selling today vs scheduled for tomorrow to achieve verified freight savings.
    """

    @staticmethod
    def calculate_freight(weight_kg: float, distance_km: float) -> Tuple[float, Dict[str, Any]]:
        w = max(weight_kg, 1.0)
        dist = max(distance_km, 5.0)

        if w <= 1000:
            vehicle = FLEET_TIERS[0]
        elif w <= 1500:
            vehicle = FLEET_TIERS[1]
        else:
            vehicle = FLEET_TIERS[2]

        total_freight = round(vehicle["base_charge"] + (dist * vehicle["per_km_rate"]) + vehicle["handling_cost"], 2)
        return total_freight, vehicle

    @classmethod
    def evaluate_alignment_opportunity(
        cls,
        lot: HarvestLot,
        target_mandi: str,
        db: Session
    ) -> Optional[Dict[str, Any]]:
        """
        Evaluates if joining tomorrow's pre-scheduled harvest produces genuine net positive savings for both farmers.
        """
        farmer_b = db.query(Farmer).filter(
            (Farmer.farmer_id == lot.farmer_id) |
            (Farmer.phone == lot.farmer_id) |
            (Farmer.phone.endswith(lot.farmer_id))
        ).first()
        if not farmer_b:
            return None

        # Determine target tomorrow date
        try:
            today_dt = datetime.strptime(lot.harvest_date, "%Y-%m-%d")
        except Exception:
            today_dt = datetime.now(timezone.utc)
        tomorrow_str = (today_dt + timedelta(days=1)).strftime("%Y-%m-%d")

        # Query candidates: other farmers with scheduled harvests for tomorrow
        candidates = db.query(HarvestLot).filter(
            HarvestLot.farmer_id != lot.farmer_id,
            HarvestLot.harvest_date == tomorrow_str,
            HarvestLot.status == "ready_for_sale"
        ).all()

        if not candidates:
            # Fallback: look for any other scheduled lot in the next 1-2 days if none tomorrow
            alt_date = (today_dt + timedelta(days=2)).strftime("%Y-%m-%d")
            candidates = db.query(HarvestLot).filter(
                HarvestLot.farmer_id != lot.farmer_id,
                HarvestLot.harvest_date == alt_date,
                HarvestLot.status == "ready_for_sale"
            ).all()

        if not candidates:
            return None

        mandi_coords = DistanceService.resolve_coordinates(target_mandi)
        b_coords = DistanceService.resolve_coordinates(farmer_b.village or farmer_b.district or "Kolar")
        dist_b_to_mandi = DistanceService.calculate_haversine_road_distance(
            b_coords[0], b_coords[1], mandi_coords[0], mandi_coords[1]
        )

        solo_cost_b, _ = cls.calculate_freight(lot.quantity_kg, dist_b_to_mandi)

        # Evaluate candidates to find the highest-savings partner
        best_match = None
        highest_combined_savings = -1.0

        for cand_lot in candidates:
            farmer_a = db.query(Farmer).filter(
                (Farmer.farmer_id == cand_lot.farmer_id) |
                (Farmer.phone == cand_lot.farmer_id) |
                (Farmer.phone.endswith(cand_lot.farmer_id))
            ).first()
            if not farmer_a:
                continue

            a_coords = DistanceService.resolve_coordinates(farmer_a.village or farmer_a.district or "Kolar")
            dist_a_to_mandi = DistanceService.calculate_haversine_road_distance(
                a_coords[0], a_coords[1], mandi_coords[0], mandi_coords[1]
            )

            solo_cost_a, _ = cls.calculate_freight(cand_lot.quantity_kg, dist_a_to_mandi)

            # Combined pooled weight and route
            combined_weight = lot.quantity_kg + cand_lot.quantity_kg
            if combined_weight > 2500:
                continue # Exceeds max single vehicle fleet payload

            dist_between_farms = DistanceService.calculate_haversine_road_distance(
                b_coords[0], b_coords[1], a_coords[0], a_coords[1]
            )

            # Route: Visit outer farm -> pick up second farm -> Mandi
            pooled_distance = dist_between_farms + min(dist_a_to_mandi, dist_b_to_mandi)
            pooled_total_cost, rec_vehicle = cls.calculate_freight(combined_weight, pooled_distance)

            # Proportional cost split based on weight
            share_a = round(pooled_total_cost * (cand_lot.quantity_kg / combined_weight), 2)
            share_b = round(pooled_total_cost * (lot.quantity_kg / combined_weight), 2)

            savings_a = round(solo_cost_a - share_a, 2)
            savings_b = round(solo_cost_b - share_b, 2)

            # STRICT RULE: Must result in genuine net positive savings for BOTH farmers!
            if savings_a > 0 and savings_b > 0:
                combined_savings = savings_a + savings_b
                if combined_savings > highest_combined_savings:
                    highest_combined_savings = combined_savings
                    best_match = {
                        "farmer_a": farmer_a,
                        "cand_lot": cand_lot,
                        "solo_cost_a": solo_cost_a,
                        "share_a": share_a,
                        "savings_a": savings_a,
                        "solo_cost_b": solo_cost_b,
                        "share_b": share_b,
                        "savings_b": savings_b,
                        "combined_weight": combined_weight,
                        "pooled_total_cost": pooled_total_cost,
                        "rec_vehicle": rec_vehicle,
                        "target_date": cand_lot.harvest_date
                    }

        if not best_match:
            return None

        # Check if an opportunity already exists for these two lots
        existing_opp = db.query(PoolingOpportunity).filter(
            PoolingOpportunity.farmer_a_lot_id == best_match["cand_lot"].lot_id,
            PoolingOpportunity.farmer_b_lot_id == lot.lot_id,
            PoolingOpportunity.status.in_(["PROPOSED", "MUTUALLY_ACCEPTED"])
        ).first()

        if existing_opp:
            return cls._format_opportunity(existing_opp, db)

        # Create new PoolingOpportunity in DB
        import uuid
        date_str = datetime.now(timezone.utc).strftime("%Y%m%d")
        opp_id = f"OPP-{date_str}-{uuid.uuid4().hex[:6].upper()}"

        opportunity = PoolingOpportunity(
            opportunity_id=opp_id,
            crop=lot.crop,
            target_mandi=target_mandi,
            target_date=best_match["target_date"],
            farmer_a_id=best_match["farmer_a"].farmer_id,
            farmer_a_lot_id=best_match["cand_lot"].lot_id,
            farmer_a_consent="PENDING",
            farmer_a_solo_cost=best_match["solo_cost_a"],
            farmer_a_pooled_cost=best_match["share_a"],
            farmer_a_savings=best_match["savings_a"],
            farmer_b_id=farmer_b.farmer_id,
            farmer_b_lot_id=lot.lot_id,
            farmer_b_consent="PENDING",
            farmer_b_solo_cost=best_match["solo_cost_b"],
            farmer_b_pooled_cost=best_match["share_b"],
            farmer_b_savings=best_match["savings_b"],
            combined_weight_kg=best_match["combined_weight"],
            recommended_vehicle_id=best_match["rec_vehicle"]["id"],
            status="PROPOSED"
        )
        db.add(opportunity)

        # Dispatch Proposal Notification to Farmer B
        notif_b = NotificationRecord(
            recipient_id=farmer_b.farmer_id,
            title=f"🤝 Co-Transport Opportunity: Save ₹{best_match['savings_b']} by Selling Tomorrow",
            body=(
                f"Farmer {best_match['farmer_a'].name} ({best_match['farmer_a'].village}) is scheduled to harvest "
                f"{best_match['cand_lot'].quantity_kg}kg {best_match['cand_lot'].crop} tomorrow for {target_mandi}. "
                f"If you wait 1 day and pool transport, your freight drops from ₹{best_match['solo_cost_b']} to "
                f"₹{best_match['share_b']} (Saving ₹{best_match['savings_b']}!). Tap to join."
            ),
            type="pooling_proposal",
            read=False,
            metadata_json=json.dumps({
                "opportunity_id": opp_id,
                "role": "FARMER_B",
                "target_date": best_match["target_date"],
                "target_mandi": target_mandi,
                "savings_inr": best_match["savings_b"],
                "pooled_cost_inr": best_match["share_b"],
                "solo_cost_inr": best_match["solo_cost_b"],
                "partner_village": best_match["farmer_a"].village,
                "action_type": "CONSENT_REQUEST"
            })
        )
        db.add(notif_b)

        # Dispatch Proposal Notification to Farmer A
        notif_a = NotificationRecord(
            recipient_id=best_match["farmer_a"].farmer_id,
            title=f"🤝 Co-Transport Request: Save ₹{best_match['savings_a']} on Tomorrow's Mandi Run",
            body=(
                f"Farmer {farmer_b.name} ({farmer_b.village}) wants to pool {lot.quantity_kg}kg {lot.crop} "
                f"with your scheduled shipment to {target_mandi} tomorrow. Sharing transport reduces your freight from "
                f"₹{best_match['solo_cost_a']} to ₹{best_match['share_a']} (Saving ₹{best_match['savings_a']}!). Tap to accept."
            ),
            type="pooling_proposal",
            read=False,
            metadata_json=json.dumps({
                "opportunity_id": opp_id,
                "role": "FARMER_A",
                "target_date": best_match["target_date"],
                "target_mandi": target_mandi,
                "savings_inr": best_match["savings_a"],
                "pooled_cost_inr": best_match["share_a"],
                "solo_cost_inr": best_match["solo_cost_a"],
                "partner_village": farmer_b.village,
                "action_type": "CONSENT_REQUEST"
            })
        )
        db.add(notif_a)

        db.commit()
        db.refresh(opportunity)
        return cls._format_opportunity(opportunity, db)

    @classmethod
    def record_farmer_consent(
        cls,
        opportunity_id: str,
        farmer_id: str,
        consent: bool,
        db: Session
    ) -> Dict[str, Any]:
        """
        Records bilateral consent. If both accept, dispatches mutual Android notifications with contact numbers.
        """
        opp = db.query(PoolingOpportunity).filter(
            PoolingOpportunity.opportunity_id == opportunity_id
        ).first()

        if not opp:
            raise ValueError(f"Pooling opportunity {opportunity_id} not found.")

        if opp.status in ["DECLINED", "EXPIRED"]:
            return {"success": False, "status": opp.status, "message": "This pooling opportunity is no longer active."}

        clean_fid = str(farmer_id).strip()
        farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()
        id_set = {clean_fid}
        if farmer:
            id_set.add(farmer.farmer_id)
            if farmer.phone:
                id_set.add(farmer.phone)

        farmer_a = db.query(Farmer).filter(
            (Farmer.farmer_id == opp.farmer_a_id) |
            (Farmer.phone == opp.farmer_a_id) |
            (Farmer.phone.endswith(opp.farmer_a_id))
        ).first()
        farmer_b = db.query(Farmer).filter(
            (Farmer.farmer_id == opp.farmer_b_id) |
            (Farmer.phone == opp.farmer_b_id) |
            (Farmer.phone.endswith(opp.farmer_b_id))
        ).first()

        now = datetime.now(timezone.utc)

        if opp.farmer_a_id in id_set:
            opp.farmer_a_consent = "ACCEPTED" if consent else "DECLINED"
        elif opp.farmer_b_id in id_set:
            opp.farmer_b_consent = "ACCEPTED" if consent else "DECLINED"
        else:
            raise ValueError(f"Farmer {farmer_id} is not a participant in this opportunity.")

        # If either declined -> mark opportunity DECLINED and notify counterpart
        if not consent:
            opp.status = "DECLINED"
            opp.settled_at = now
            declining_farmer = farmer_a if farmer_id == opp.farmer_a_id else farmer_b
            other_farmer = farmer_b if farmer_id == opp.farmer_a_id else farmer_a

            notif_decline = NotificationRecord(
                recipient_id=other_farmer.farmer_id,
                title="❌ Co-Transport Pool Declined",
                body=f"The co-transport proposal for {opp.target_mandi} was declined. Your scheduled solo transit remains active.",
                type="pooling_declined",
                read=False,
                metadata_json=json.dumps({"opportunity_id": opp.opportunity_id, "status": "DECLINED"})
            )
            db.add(notif_decline)
            db.commit()
            return {"success": True, "status": "DECLINED", "message": "Opportunity declined."}

        # If both accepted -> mark MUTUALLY_ACCEPTED and share contact details!
        if opp.farmer_a_consent == "ACCEPTED" and opp.farmer_b_consent == "ACCEPTED":
            opp.status = "MUTUALLY_ACCEPTED"
            opp.settled_at = now

            # Reschedule Farmer B's lot to the agreed target date
            lot_b = db.query(HarvestLot).filter(HarvestLot.lot_id == opp.farmer_b_lot_id).first()
            if lot_b:
                lot_b.harvest_date = opp.target_date
                lot_b.sell_immediately = False
                lot_b.status = "pooled"

            lot_a = db.query(HarvestLot).filter(HarvestLot.lot_id == opp.farmer_a_lot_id).first()
            if lot_a:
                lot_a.status = "pooled"

            # Mutual Contact Exchange via Notifications
            # Notification to Farmer A with Farmer B's phone
            notif_to_a = NotificationRecord(
                recipient_id=farmer_a.farmer_id,
                title="🤝 Pool Confirmed! Partner Details Attached",
                body=(
                    f"Farmer {farmer_b.name} ({farmer_b.village}) has accepted! "
                    f"Phone: {farmer_b.phone}. Call to coordinate aggregate pickup for tomorrow's run to {opp.target_mandi}."
                ),
                type="contact_exchange",
                read=False,
                metadata_json=json.dumps({
                    "opportunity_id": opp.opportunity_id,
                    "contact_name": farmer_b.name,
                    "contact_phone": farmer_b.phone,
                    "village": farmer_b.village,
                    "crop": opp.crop,
                    "target_date": opp.target_date,
                    "target_mandi": opp.target_mandi,
                    "savings_inr": opp.farmer_a_savings,
                    "action": "CALL"
                })
            )
            db.add(notif_to_a)

            # Notification to Farmer B with Farmer A's phone
            notif_to_b = NotificationRecord(
                recipient_id=farmer_b.farmer_id,
                title="🤝 Pool Confirmed! Partner Details Attached",
                body=(
                    f"Farmer {farmer_a.name} ({farmer_a.village}) has accepted! "
                    f"Phone: {farmer_a.phone}. Call to coordinate aggregate pickup for tomorrow's run to {opp.target_mandi}."
                ),
                type="contact_exchange",
                read=False,
                metadata_json=json.dumps({
                    "opportunity_id": opp.opportunity_id,
                    "contact_name": farmer_a.name,
                    "contact_phone": farmer_a.phone,
                    "village": farmer_a.village,
                    "crop": opp.crop,
                    "target_date": opp.target_date,
                    "target_mandi": opp.target_mandi,
                    "savings_inr": opp.farmer_b_savings,
                    "action": "CALL"
                })
            )
            db.add(notif_to_b)

            # Audit Log
            log = AuditLog(
                action="CO_TRANSPORT_POOL_CONFIRMED",
                actor_role="PLATFORM",
                actor_id=opp.opportunity_id,
                details=f"Bilateral consent confirmed between {farmer_a.name} and {farmer_b.name} for {opp.target_mandi} on {opp.target_date}."
            )
            db.add(log)
            db.commit()

            return {
                "success": True,
                "status": "MUTUALLY_ACCEPTED",
                "message": "Both farmers consented! Contact details exchanged via notifications.",
                "partner_details": {
                    "name": (farmer_b.name if farmer_id == opp.farmer_a_id else farmer_a.name),
                    "phone": (farmer_b.phone if farmer_id == opp.farmer_a_id else farmer_a.phone),
                    "village": (farmer_b.village if farmer_id == opp.farmer_a_id else farmer_a.village),
                }
            }

        # Otherwise one has accepted and waiting for counterpart
        db.commit()
        return {
            "success": True,
            "status": "AWAITING_COUNTERPART",
            "message": "Your consent has been recorded. Awaiting response from counterpart farmer."
        }

    @classmethod
    def get_farmer_opportunities(cls, farmer_id: str, db: Session) -> List[Dict[str, Any]]:
        clean_fid = str(farmer_id).strip()
        farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()

        id_set = {clean_fid}
        if farmer:
            id_set.add(farmer.farmer_id)
            if farmer.phone:
                id_set.add(farmer.phone)

        opps = db.query(PoolingOpportunity).filter(
            (PoolingOpportunity.farmer_a_id.in_(id_set)) | (PoolingOpportunity.farmer_b_id.in_(id_set))
        ).order_by(PoolingOpportunity.created_at.desc()).all()

        return [cls._format_opportunity(o, db, current_farmer_id=clean_fid, farmer_id_set=id_set) for o in opps]

    @classmethod
    def _format_opportunity(
        cls,
        opp: PoolingOpportunity,
        db: Session,
        current_farmer_id: Optional[str] = None,
        farmer_id_set: Optional[set] = None
    ) -> Dict[str, Any]:
        farmer_a = db.query(Farmer).filter(
            (Farmer.farmer_id == opp.farmer_a_id) |
            (Farmer.phone == opp.farmer_a_id) |
            (Farmer.phone.endswith(opp.farmer_a_id))
        ).first()
        farmer_b = db.query(Farmer).filter(
            (Farmer.farmer_id == opp.farmer_b_id) |
            (Farmer.phone == opp.farmer_b_id) |
            (Farmer.phone.endswith(opp.farmer_b_id))
        ).first()

        is_a = current_farmer_id == opp.farmer_a_id or (farmer_id_set is not None and opp.farmer_a_id in farmer_id_set)

        # Disclose contact details ONLY if mutually accepted
        show_contacts = (opp.status == "MUTUALLY_ACCEPTED")

        return {
            "opportunity_id": opp.opportunity_id,
            "crop": opp.crop,
            "target_mandi": opp.target_mandi,
            "target_date": opp.target_date,
            "status": opp.status,
            "combined_weight_kg": opp.combined_weight_kg,
            "recommended_vehicle": opp.recommended_vehicle_id,
            "my_role": "FARMER_A" if is_a else "FARMER_B",
            "my_consent": opp.farmer_a_consent if is_a else opp.farmer_b_consent,
            "partner_consent": opp.farmer_b_consent if is_a else opp.farmer_a_consent,
            "my_solo_cost": opp.farmer_a_solo_cost if is_a else opp.farmer_b_solo_cost,
            "my_pooled_cost": opp.farmer_a_pooled_cost if is_a else opp.farmer_b_pooled_cost,
            "my_savings": opp.farmer_a_savings if is_a else opp.farmer_b_savings,
            "partner_name": (farmer_b.name if is_a else farmer_a.name) if (farmer_a and farmer_b) else "",
            "partner_village": (farmer_b.village if is_a else farmer_a.village) if (farmer_a and farmer_b) else "",
            "partner_phone": ((farmer_b.phone if is_a else farmer_a.phone) if show_contacts and farmer_a and farmer_b else None),
            "created_at": opp.created_at.isoformat() if opp.created_at else None
        }
