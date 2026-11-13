import json
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, Any, List
from sqlalchemy.orm import Session

from backend.app.models.models import Farmer, HarvestLot, NotificationRecord, AuditLog
from backend.app.services.weather_service import WeatherService

class WeatherProtectionService:
    """
    Proactive Weather-Triggered Harvest Protection & Rescheduling Service.
    Detects severe weather events on scheduled harvest days and advises delaying picking to protect produce value.
    """

    @classmethod
    def check_scheduled_harvest_weather_risk(
        cls,
        farmer_id: str,
        db: Session,
        language_code: str = "kn-IN"
    ) -> List[Dict[str, Any]]:
        clean_fid = str(farmer_id).strip()
        farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()
        if not farmer:
            return []

        canonical_fid = farmer.farmer_id
        location = farmer.village or farmer.district or "Bengaluru"
        weather_data = WeatherService.get_weather(location)

        today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

        # Find lots scheduled for today
        scheduled_lots = db.query(HarvestLot).filter(
            HarvestLot.farmer_id.in_([canonical_fid, clean_fid]),
            HarvestLot.harvest_date == today_str,
            HarvestLot.status == "ready_for_sale"
        ).all()

        if not scheduled_lots:
            return []

        # Analyze today's weather risk
        precip_mm = weather_data.get("precip_mm", 0.0)
        risk_level = weather_data.get("weather_risk_level", "LOW")
        condition = weather_data.get("condition", "Partly Cloudy")
        forecast = weather_data.get("forecast_3day", [])

        # Check tomorrow's condition
        tomorrow_weather = forecast[1] if len(forecast) > 1 else {"condition": "Clear", "rain_chance_pct": 10}
        tomorrow_condition = tomorrow_weather.get("condition", "Sunny/Dry")
        tomorrow_rain_chance = tomorrow_weather.get("rain_chance_pct", 10)

        # Trigger condition: Heavy rain or high weather risk today, but tomorrow is favorable
        is_high_risk = (
            risk_level in ["HIGH", "MODERATE"] or
            precip_mm >= 2.0 or
            any(w in condition.lower() for w in ["rain", "storm", "thunder", "shower"])
        )

        risks = []
        for lot in scheduled_lots:
            advisory_en = (
                f"⛈️ Rain/Moisture Alert: Heavy rain or wet conditions predicted today in {location} ({condition}, {precip_mm}mm). "
                f"Harvesting in wet conditions triggers rapid bacterial soft rot in crates, soil adhesion, and 15%-25% APMC price markdowns. "
                f"Tomorrow's forecast is {tomorrow_condition} with only {tomorrow_rain_chance}% rain chance. "
                f"Delaying harvest by 24 hours protects produce quality and shelf life."
            )
            advisory_kn = (
                f"⛈️ ಮಳೆ ಎಚ್ಚರಿಕೆ: ಇಂದು {location} ನಲ್ಲಿ ತೀವ್ರ ಮಳೆಯ ಸಂಭವವಿದೆ ({condition}). "
                f"ತೇವಾಂಶದಲ್ಲಿ ಕೊಯ್ಲು ಮಾಡುವುದರಿಂದ ತರಕಾರಿ ಕೊಳೆಯುವ ಅಪಾಯವಿದೆ ಮತ್ತು ಮಾರುಕಟ್ಟೆಯಲ್ಲಿ ಕಡಿಮೆ ಬೆಲೆ ಸಿಗುತ್ತದೆ. "
                f"ನಾಳೆ ಹವಾಮಾನವು ಉತ್ತಮವಾಗಿದೆ ({tomorrow_condition}). ದಯವಿಟ್ಟು ನಾಳೆಗೆ ಕೊಯ್ಲನ್ನು ಮುಂದೂಡಿ."
            )

            from backend.app.services.sarvam_service import SarvamService
            target_lang = SarvamService.normalize_language_code(language_code or "kn-IN")
            if target_lang == "kn-IN":
                advisory_localized = advisory_kn
            elif target_lang == "en-IN":
                advisory_localized = advisory_en
            else:
                try:
                    trans = SarvamService.translate_text(
                        text=advisory_en,
                        target_language_code=target_lang,
                        source_language_code="en-IN"
                    )
                    advisory_localized = trans.get("translated_text", advisory_en)
                except Exception:
                    advisory_localized = advisory_en

            # Auto-dispatch notification if not already sent for this lot today
            existing_notif = db.query(NotificationRecord).filter(
                NotificationRecord.recipient_id == farmer_id,
                NotificationRecord.type == "weather_reschedule_alert",
                NotificationRecord.created_at >= datetime.now(timezone.utc).replace(hour=0, minute=0, second=0)
            ).first()

            if not existing_notif:
                notif = NotificationRecord(
                    recipient_id=farmer_id,
                    title="⛈️ Weather Alert: Postpone Harvest to Tomorrow",
                    body=advisory_localized if target_lang != "en-IN" else advisory_en,
                    type="weather_reschedule_alert",
                    read=False,
                    metadata_json=json.dumps({
                        "lot_id": lot.lot_id,
                        "crop": lot.crop,
                        "current_date": today_str,
                        "recommended_date": tomorrow_str,
                        "action": "RESCHEDULE_WEATHER",
                        "risk_level": risk_level,
                        "language_code": target_lang
                    })
                )
                db.add(notif)
                db.commit()

            risks.append({
                "lot_id": lot.lot_id,
                "crop": lot.crop,
                "quantity_kg": lot.quantity_kg,
                "current_scheduled_date": lot.harvest_date,
                "recommended_reschedule_date": tomorrow_str,
                "weather_risk_level": risk_level if is_high_risk else "EVALUATED_SAFE",
                "today_weather": {
                    "location": location,
                    "condition": condition,
                    "precip_mm": precip_mm,
                    "temperature_c": weather_data.get("temperature_c", 26)
                },
                "tomorrow_weather": {
                    "condition": tomorrow_condition,
                    "rain_chance_pct": tomorrow_rain_chance
                },
                "advisory_en": advisory_en if is_high_risk else "Weather conditions are suitable for harvest today.",
                "advisory_kn": advisory_kn if is_high_risk else "ಇಂದು ಕೊಯ್ಲು ಮಾಡಲು ಹವಾಮಾನ ಸೂಕ್ತವಾಗಿದೆ.",
                "advisory_localized": advisory_localized if is_high_risk else "Weather conditions are suitable for harvest today.",
                "language_code": target_lang,
                "should_reschedule": is_high_risk
            })

        return risks

    @classmethod
    def reschedule_harvest_for_weather(
        cls,
        lot_id: str,
        new_date: Optional[str],
        db: Session
    ) -> Dict[str, Any]:
        lot = db.query(HarvestLot).filter(HarvestLot.lot_id == lot_id).first()
        if not lot:
            raise ValueError(f"Harvest lot {lot_id} not found.")

        old_date = lot.harvest_date
        target_date = new_date or (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

        lot.harvest_date = target_date
        lot.sell_immediately = False

        # Parse & update metadata
        meta = {}
        try:
            if lot.metadata_json:
                meta = json.loads(lot.metadata_json)
        except Exception:
            meta = {}
        meta["weather_rescheduled"] = True
        meta["original_date"] = old_date
        meta["rescheduled_at"] = datetime.now(timezone.utc).isoformat()
        lot.metadata_json = json.dumps(meta)

        # Audit log
        log = AuditLog(
            action="WEATHER_RESCHEDULED",
            actor_role="FARMER",
            actor_id=lot.farmer_id,
            details=f"Lot {lot_id} rescheduled from {old_date} to {target_date} due to weather protection alert."
        )
        db.add(log)

        # Confirmation Notification
        notif = NotificationRecord(
            recipient_id=lot.farmer_id,
            title="✅ Harvest Rescheduled for Weather Safety",
            body=f"Your {lot.crop} harvest (Lot {lot.lot_id}) has been successfully rescheduled to {target_date}. Sunny/dry conditions expected.",
            type="weather_reschedule_confirmed",
            read=False,
            metadata_json=json.dumps({"lot_id": lot_id, "new_date": target_date})
        )
        db.add(notif)
        db.commit()
        db.refresh(lot)

        return {
            "success": True,
            "lot_id": lot_id,
            "crop": lot.crop,
            "previous_harvest_date": old_date,
            "new_harvest_date": target_date,
            "status": "RESCHEDULED",
            "message": f"Harvest successfully rescheduled to {target_date}."
        }
