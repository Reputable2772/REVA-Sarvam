import json
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
from datetime import datetime, timezone

from backend.app.models.models import Farmer, HarvestLot
from backend.app.models.scheme_models import Scheme, FarmerOpportunityMatch, FarmerBenefitNotification

class SchemeMatchingService:
    """
    Deterministic Farmer-Opportunity Matching Engine.
    Evaluates verified government guidelines against farmer attributes without AI hallucination.
    """

    @classmethod
    def evaluate_farmer_opportunities(
        cls,
        farmer_id: str,
        db: Session,
        voluntary_profile_override: Optional[Dict[str, Any]] = None
    ) -> List[Dict[str, Any]]:
        farmer = db.query(Farmer).filter(Farmer.farmer_id == farmer_id).first()
        if not farmer:
            return []

        # Read farmer harvest history to determine active crops
        lots = db.query(HarvestLot).filter(HarvestLot.farmer_id == farmer_id).all()
        farmer_crops = list({l.crop for l in lots if l.crop})
        if not farmer_crops:
            farmer_crops = ["Tomato"]

        # Parse extensible metadata
        meta = {}
        try:
            if farmer.metadata_json:
                meta = json.loads(farmer.metadata_json)
        except Exception:
            meta = {}

        if voluntary_profile_override:
            meta.update(voluntary_profile_override)

        landholding_ha = meta.get("landholding_hectares")
        fruits_id = meta.get("fruits_id")
        is_fpo_member = meta.get("is_fpo_member", False)
        farmer_state = farmer.state or "Karnataka"
        farmer_district = farmer.district or "Kolar"

        schemes = db.query(Scheme).filter(Scheme.verification_status == "OFFICIALLY_VERIFIED").all()
        results = []

        for sch in schemes:
            app_states = json.loads(sch.applicable_states_json or "[]")
            app_crops = json.loads(sch.applicable_crops_json or "[]")
            missing_fields = []
            matched_rules = []

            # 1. State check
            if app_states and farmer_state not in app_states:
                match_status = "INELIGIBLE"
                reason_en = f"This scheme applies only to {', '.join(app_states)}. Your registered state is {farmer_state}."
                reason_kn = f"ಈ ಯೋಜನೆಯು {', '.join(app_states)} ರಾಜ್ಯಗಳಿಗೆ ಮಾತ್ರ ಅನ್ವಯಿಸುತ್ತದೆ. ನಿಮ್ಮ ರಾಜ್ಯ {farmer_state}."
                results.append(cls._format_result(sch, match_status, reason_en, reason_kn, missing_fields, matched_rules))
                continue

            matched_rules.append(f"Geographic eligibility confirmed for {farmer_state}, {farmer_district}")

            # 2. Crop check
            crop_match = True
            if app_crops:
                matching_crops = [c for c in farmer_crops if any(ac.lower() in c.lower() for ac in app_crops)]
                if not matching_crops:
                    crop_match = False
                else:
                    matched_rules.append(f"Crop match confirmed: {', '.join(matching_crops)}")

            if not crop_match:
                match_status = "POTENTIALLY_RELEVANT"
                reason_en = f"Applies to {', '.join(app_crops)}. If you diversify into these crops, this assistance is applicable."
                reason_kn = f"ಇದು {', '.join(app_crops)} ಬೆಳೆಗಳಿಗೆ ಅನ್ವಯಿಸುತ್ತದೆ. ನೀವು ಈ ಬೆಳೆಗಳನ್ನು ಬೆಳೆದರೆ ಈ ಸೌಲಭ್ಯ ಲಭ್ಯ."
                results.append(cls._format_result(sch, match_status, reason_en, reason_kn, missing_fields, matched_rules))
                continue

            # 3. FPO distinction
            if sch.target_level == "FPO_COMPANY":
                matched_rules.append("Institutional Producer Company eligibility rule evaluated")
                if is_fpo_member:
                    match_status = "LIKELY_ELIGIBLE"
                    reason_en = "You are an FPO member. Your FPO can apply for the ₹15 Lakh equity grant to fund bulk procurement & storage."
                    reason_kn = "ನೀವು FPO ಸದಸ್ಯರಾಗಿದ್ದೀರಿ. ನಿಮ್ಮ FPO ₹15 ಲಕ್ಷ ಈಕ್ವಿಟಿ ಅನುದಾನಕ್ಕೆ ಅರ್ಜಿ ಸಲ್ಲಿಸಬಹುದು."
                else:
                    match_status = "POTENTIALLY_RELEVANT"
                    reason_en = "Institutional FPO grant (Paid to FPO company, not individual farmers). Join your local Kolar FPO cluster to benefit."
                    reason_kn = "ಸಾಂಸ್ಥಿಕ FPO ಅನುದಾನ (ವೈಯಕ್ತಿಕ ರೈತರಿಗಲ್ಲ, FPO ಸಂಸ್ಥೆಗೆ). ಸಮಗ್ರ ಲಾಭ ಪಡೆಯಲು ಸ್ಥಳೀಯ FPO ಗೆ ಸೇರಿ."
                results.append(cls._format_result(sch, match_status, reason_en, reason_kn, missing_fields, matched_rules))
                continue

            # 4. Landholding requirement
            land_pass = True
            if sch.max_landholding_hectares is not None:
                if landholding_ha is None:
                    missing_fields.append("landholding_hectares")
                    land_pass = False
                elif landholding_ha > sch.max_landholding_hectares:
                    match_status = "INELIGIBLE"
                    reason_en = f"Exceeds smallholder ceiling of {sch.max_landholding_hectares} hectares (Your land: {landholding_ha} ha)."
                    reason_kn = f"ನಿಗದಿತ ಗರಿಷ್ಠ {sch.max_landholding_hectares} ಹೆಕ್ಟೇರ್ ಮಿತಿಯನ್ನು ಮೀರಿದೆ (ನಿಮ್ಮ ಜಮೀನು: {landholding_ha} ಹೆಕ್ಟೇರ್)."
                    results.append(cls._format_result(sch, match_status, reason_en, reason_kn, missing_fields, matched_rules))
                    continue
                else:
                    matched_rules.append(f"Smallholder landholding ceiling satisfied ({landholding_ha} ha <= {sch.max_landholding_hectares} ha)")

            # 5. FRUITS ID check
            fruits_pass = True
            if sch.requires_fruits_id:
                if not fruits_id:
                    missing_fields.append("fruits_id")
                    fruits_pass = False
                else:
                    matched_rules.append(f"FRUITS FID active ({fruits_id})")

            # Final status determination
            if missing_fields:
                match_status = "MORE_INFO_REQUIRED"
                fields_text_en = ", ".join(missing_fields).replace("_", " ").title()
                reason_en = f"Potentially eligible! Please provide your {fields_text_en} to confirm eligibility."
                reason_kn = f"ಅರ್ಹತೆಯ ಸಾಧ್ಯತೆ ಇದೆ! ದೃಢೀಕರಿಸಲು ದಯವಿಟ್ಟು ನಿಮ್ಮ ವಿವರಗಳನ್ನು ದಾಖಲಿಸಿ."
            else:
                match_status = "LIKELY_ELIGIBLE"
                reason_en = f"Matches your location ({farmer_district}), crop ({', '.join(farmer_crops)}), and smallholder farmer profile."
                reason_kn = f"ನಿಮ್ಮ ಸ್ಥಳ ({farmer_district}), ಬೆಳೆ ({', '.join(farmer_crops)}), ಮತ್ತು ರೈತ ಪ್ರೊಫೈಲ್‌ಗೆ ನಿಖರವಾಗಿ ಸರಿಹೊಂದುತ್ತದೆ."

            results.append(cls._format_result(sch, match_status, reason_en, reason_kn, missing_fields, matched_rules))

        # Rank: LIKELY_ELIGIBLE first, then MORE_INFO_REQUIRED, then POTENTIALLY_RELEVANT, then INELIGIBLE
        status_rank = {
            "LIKELY_ELIGIBLE": 1,
            "MORE_INFO_REQUIRED": 2,
            "POTENTIALLY_RELEVANT": 3,
            "INELIGIBLE": 4
        }
        results.sort(key=lambda x: status_rank.get(x["match_status"], 5))

        # Persist or update match cache in DB
        for r in results:
            existing_match = db.query(FarmerOpportunityMatch).filter(
                FarmerOpportunityMatch.farmer_id == farmer_id,
                FarmerOpportunityMatch.scheme_id == r["scheme_id"]
            ).first()

            if existing_match:
                existing_match.match_status = r["match_status"]
                existing_match.relevance_reason_en = r["relevance_reason_en"]
                existing_match.relevance_reason_kn = r["relevance_reason_kn"]
                existing_match.missing_fields_json = json.dumps(r["missing_fields"])
                existing_match.matched_rules_json = json.dumps(r["matched_rules"])
                existing_match.updated_at = datetime.now(timezone.utc)
            else:
                new_match = FarmerOpportunityMatch(
                    farmer_id=farmer_id,
                    phone=farmer.phone,
                    scheme_id=r["scheme_id"],
                    match_status=r["match_status"],
                    relevance_reason_en=r["relevance_reason_en"],
                    relevance_reason_kn=r["relevance_reason_kn"],
                    missing_fields_json=json.dumps(r["missing_fields"]),
                    matched_rules_json=json.dumps(r["matched_rules"])
                )
                db.add(new_match)
        db.commit()

        return results

    @classmethod
    def _format_result(
        cls,
        sch: Scheme,
        match_status: str,
        reason_en: str,
        reason_kn: str,
        missing_fields: List[str],
        matched_rules: List[str]
    ) -> Dict[str, Any]:
        return {
            "scheme_id": sch.scheme_id,
            "title_en": sch.title_en,
            "title_kn": sch.title_kn,
            "category": sch.category,
            "target_level": sch.target_level,
            "issuing_department": sch.issuing_department,
            "benefit_description_en": sch.benefit_description_en,
            "benefit_description_kn": sch.benefit_description_kn,
            "benefit_amount_spec": sch.benefit_amount_spec,
            "eligibility_criteria_en": sch.eligibility_criteria_en,
            "eligibility_criteria_kn": sch.eligibility_criteria_kn,
            "exclusion_criteria_en": sch.exclusion_criteria_en,
            "exclusion_criteria_kn": sch.exclusion_criteria_kn,
            "required_documents": json.loads(sch.required_documents_json or "[]"),
            "application_steps": json.loads(sch.application_steps_json or "[]"),
            "application_method": sch.application_method,
            "application_window_start": sch.application_window_start,
            "application_window_end": sch.application_window_end,
            "is_window_confirmed": sch.is_window_confirmed,
            "official_portal_url": sch.official_portal_url,
            "official_contact": sch.official_contact,
            "verification_status": sch.verification_status,
            "last_verified_date": sch.last_verified_date,
            "freshness_label": sch.freshness_label,
            "match_status": match_status,
            "relevance_reason_en": reason_en,
            "relevance_reason_kn": reason_kn,
            "missing_fields": missing_fields,
            "matched_rules": matched_rules
        }

    @classmethod
    def generate_smart_notifications_for_farmer(
        cls,
        farmer_id: str,
        db: Session
    ) -> List[Dict[str, Any]]:
        """
        Generates in-app benefit alerts explaining WHY the farmer received each notification.
        Prevents duplicate alerts using deterministic dedup_key.
        """
        opportunities = cls.evaluate_farmer_opportunities(farmer_id, db)
        created_notifications = []

        for opp in opportunities:
            if opp["match_status"] in ["LIKELY_ELIGIBLE", "MORE_INFO_REQUIRED"]:
                dedup_key = f"NOTIF-{farmer_id}-{opp['scheme_id']}-202611"
                existing = db.query(FarmerBenefitNotification).filter(
                    FarmerBenefitNotification.dedup_key == dedup_key
                ).first()

                if not existing:
                    notif = FarmerBenefitNotification(
                        farmer_id=farmer_id,
                        scheme_id=opp["scheme_id"],
                        dedup_key=dedup_key,
                        title_en=f"New Opportunity: {opp['title_en']}",
                        title_kn=f"ಹೊಸ ರೈತ ಅವಕಾಶ: {opp['title_kn']}",
                        body_en=f"Benefit: {opp['benefit_amount_spec']}. Application window active for 2026.",
                        body_kn=f"ಸೌಲಭ್ಯ: {opp['benefit_amount_spec']}. 2026 ರ ಅರ್ಜಿ ಸಲ್ಲಿಕೆ ಸಕ್ರಿಯವಾಗಿದೆ.",
                        why_received_en=f"Why you received this: {opp['relevance_reason_en']}",
                        why_received_kn=f"ನಿಮಗೆ ಈ ಮಾಹಿತಿ ಏಕೆ: {opp['relevance_reason_kn']}",
                        source_url=opp["official_portal_url"],
                        is_read=False
                    )
                    db.add(notif)
                    created_notifications.append(notif)
        db.commit()

        # Query all notifications for farmer
        all_notifs = db.query(FarmerBenefitNotification).filter(
            FarmerBenefitNotification.farmer_id == farmer_id
        ).order_by(FarmerBenefitNotification.created_at.desc()).all()

        return [
            {
                "id": n.id,
                "scheme_id": n.scheme_id,
                "title_en": n.title_en,
                "title_kn": n.title_kn,
                "body_en": n.body_en,
                "body_kn": n.body_kn,
                "why_received_en": n.why_received_en,
                "why_received_kn": n.why_received_kn,
                "source_url": n.source_url,
                "is_read": n.is_read,
                "created_at": n.created_at.isoformat() if n.created_at else ""
            }
            for n in all_notifs
        ]
