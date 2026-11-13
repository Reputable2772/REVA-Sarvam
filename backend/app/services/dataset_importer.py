import csv
import io
import json
from datetime import datetime
from typing import List, Dict, Any
from sqlalchemy.orm import Session
from backend.app.models.models import MandiPriceRecord, Farmer, HarvestLot, BuyerRequirement

class DatasetImporter:
    """
    Robust, extensible dataset ingestion engine supporting AGMARKNET daily prices,
    multi-crop smallholder harvest lots, buyer requirements, and regional corridors
    in both JSON and CSV formats.
    """

    @staticmethod
    def import_mandi_prices_from_json(db: Session, records: List[Dict[str, Any]]) -> Dict[str, Any]:
        imported = 0
        updated = 0
        for r in records:
            # Flexible field resolution supporting both AGMARKNET official column names and custom camelCase/snake_case
            market = r.get("market_name") or r.get("Market") or r.get("market")
            if not market:
                continue
            commodity = r.get("commodity") or r.get("Commodity") or "Produce"
            variety = r.get("variety") or r.get("Variety") or "Standard"
            grade = r.get("grade") or r.get("Grade") or "FAQ"
            date = str(r.get("arrival_date") or r.get("Arrival_Date") or r.get("date") or datetime.now().strftime("%Y-%m-%d"))
            arrivals = float(r.get("arrivals_tonnes") or r.get("Arrivals") or 0.0)

            min_q = float(r.get("min_price_per_quintal") or r.get("Min_x0020_Price") or r.get("min_price") or 2000.0)
            max_q = float(r.get("max_price_per_quintal") or r.get("Max_x0020_Price") or r.get("max_price") or 3000.0)
            modal_q = float(r.get("modal_price_per_quintal") or r.get("Modal_x0020_Price") or r.get("modal_price") or 2400.0)
            modal_kg = round(modal_q / 100.0, 2)

            # Check if record already exists for market, commodity, and date
            existing = db.query(MandiPriceRecord).filter(
                MandiPriceRecord.market_name == market,
                MandiPriceRecord.commodity == commodity,
                MandiPriceRecord.arrival_date == date
            ).first()

            if existing:
                existing.variety = variety
                existing.grade = grade
                existing.arrivals_tonnes = arrivals
                existing.min_price_per_quintal = min_q
                existing.max_price_per_quintal = max_q
                existing.modal_price_per_quintal = modal_q
                existing.modal_price_per_kg = modal_kg
                updated += 1
            else:
                new_record = MandiPriceRecord(
                    state=r.get("state") or "India",
                    district=r.get("district") or market,
                    market_name=market,
                    commodity=commodity,
                    variety=variety,
                    grade=grade,
                    arrival_date=date,
                    arrivals_tonnes=arrivals,
                    min_price_per_quintal=min_q,
                    max_price_per_quintal=max_q,
                    modal_price_per_quintal=modal_q,
                    modal_price_kg=modal_kg,
                    source=r.get("source") or "AGMARKNET"
                )
                db.add(new_record)
                imported += 1

        db.commit()
        return {"success": True, "imported": imported, "updated": updated, "total_processed": len(records)}

    @staticmethod
    def import_mandi_prices_from_csv(db: Session, csv_text: str) -> Dict[str, Any]:
        reader = csv.DictReader(io.StringIO(csv_text.strip()))
        records = [row for row in reader]
        return DatasetImporter.import_mandi_prices_from_json(db, records)

    @staticmethod
    def import_farmers(db: Session, records: List[Dict[str, Any]]) -> Dict[str, Any]:
        count = 0
        for r in records:
            f_id = r["farmer_id"]
            existing = db.query(Farmer).filter(Farmer.farmer_id == f_id).first()
            meta = r.get("metadata", {})
            if isinstance(meta, dict):
                meta_str = json.dumps(meta)
            else:
                meta_str = str(meta)

            if existing:
                existing.name = r.get("name", existing.name)
                existing.village = r.get("village", existing.village)
                existing.district = r.get("district", existing.district)
                existing.phone = r.get("phone", existing.phone)
                existing.latitude = r.get("latitude", existing.latitude)
                existing.longitude = r.get("longitude", existing.longitude)
                existing.metadata_json = meta_str
            else:
                new_farmer = Farmer(
                    farmer_id=f_id,
                    name=r["name"],
                    village=r.get("village", ""),
                    district=r.get("district") or r.get("village") or "",
                    phone=r.get("phone") or "+91 98000 00000",
                    latitude=r.get("latitude"),
                    longitude=r.get("longitude"),
                    metadata_json=meta_str
                )
                db.add(new_farmer)
            count += 1
        db.commit()
        return {"success": True, "count": count}

    @staticmethod
    def import_harvest_lots(db: Session, records: List[Dict[str, Any]]) -> Dict[str, Any]:
        count = 0
        for r in records:
            lot_id = r["lot_id"]
            existing = db.query(HarvestLot).filter(HarvestLot.lot_id == lot_id).first()
            meta = r.get("metadata", {})
            meta_str = json.dumps(meta) if isinstance(meta, dict) else str(meta)

            if existing:
                existing.crop = r.get("crop", existing.crop)
                existing.variety = r.get("variety", existing.variety)
                existing.quantity_kg = float(r.get("quantity_kg", existing.quantity_kg))
                existing.quality_grade = r.get("quality_grade", existing.quality_grade)
                existing.status = r.get("status", existing.status)
                existing.metadata_json = meta_str
            else:
                new_lot = HarvestLot(
                    lot_id=lot_id,
                    farmer_id=r["farmer_id"],
                    crop=r.get("crop") or "Produce",
                    variety=r.get("variety") or "Standard Variety",
                    quantity_kg=float(r["quantity_kg"]),
                    quality_grade=r.get("quality_grade") or "Grade A",
                    harvest_date=r.get("harvest_date") or datetime.now().strftime("%Y-%m-%d"),
                    status=r.get("status") or "ready_for_sale",
                    metadata_json=meta_str
                )
                db.add(new_lot)
            count += 1
        db.commit()
        return {"success": True, "count": count}

    @staticmethod
    def load_local_agmarknet_csv(db: Session, filepath: str = "datasets/9ef84268-d588-465a-a308-a864a43d0070.csv") -> Dict[str, Any]:
        """
        Loads official AGMARKNET daily CSV directly from the local filesystem.
        Optimized for Karnataka corridor mandis and major commodities.
        """
        import os
        if not os.path.exists(filepath):
            return {"success": False, "error": f"File not found: {filepath}"}

        with open(filepath, mode="r", encoding="utf-8", errors="ignore") as f:
            reader = csv.DictReader(f)
            relevant_records = []
            for row in reader:
                # Include Karnataka records or major agricultural corridor entries
                state = row.get("State", "")
                if "Karnataka" in state or "Kolar" in row.get("District", "") or "Tomato" in row.get("Commodity", ""):
                    relevant_records.append(row)

            if relevant_records:
                return DatasetImporter.import_mandi_prices_from_json(db, relevant_records)
            return {"success": True, "message": "No matching regional records found"}

    @staticmethod
    def get_summary(db: Session) -> Dict[str, Any]:
        farmers_count = db.query(Farmer).count()
        lots_count = db.query(HarvestLot).count()
        mandi_records_count = db.query(MandiPriceRecord).count()
        buyers_count = db.query(BuyerRequirement).count()

        commodities = [c[0] for c in db.query(MandiPriceRecord.commodity).distinct().all()]
        mandis = [m[0] for m in db.query(MandiPriceRecord.market_name).distinct().all()]

        return {
            "total_farmers": farmers_count,
            "total_harvest_lots": lots_count,
            "total_buyer_requirements": buyers_count,
            "total_mandi_price_records": mandi_records_count,
            "commodities_covered": commodities,
            "mandis_covered": mandis
        }
