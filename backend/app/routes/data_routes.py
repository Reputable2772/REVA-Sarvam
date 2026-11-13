from fastapi import APIRouter, Depends, HTTPException, Body
from sqlalchemy.orm import Session
from typing import List, Dict, Any, Optional
from backend.app.database import get_db
from backend.app.services.dataset_importer import DatasetImporter
from backend.app.models.models import MandiPriceRecord

router = APIRouter(prefix="/data", tags=["Dataset Importer & Market Intelligence"])

@router.post("/import/mandi-prices")
def import_mandi_prices(
    records: List[Dict[str, Any]],
    db: Session = Depends(get_db)
):
    """
    Ingest AGMARKNET, e-NAM, or Data.gov.in daily mandi prices in JSON format.
    Supports dynamic mapping of column names.
    """
    try:
        result = DatasetImporter.import_mandi_prices_from_json(db, records)
        return result
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to import mandi prices: {str(e)}")

@router.post("/import/mandi-prices-csv")
def import_mandi_prices_csv(
    csv_text: str = Body(..., media_type="text/plain"),
    db: Session = Depends(get_db)
):
    """
    Ingest AGMARKNET daily prices directly from a raw CSV payload.
    """
    try:
        result = DatasetImporter.import_mandi_prices_from_csv(db, csv_text)
        return result
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to import CSV: {str(e)}")

@router.post("/import/farmers")
def import_farmers(
    farmers: List[Dict[str, Any]],
    db: Session = Depends(get_db)
):
    try:
        return DatasetImporter.import_farmers(db, farmers)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.post("/import/lots")
def import_lots(
    lots: List[Dict[str, Any]],
    db: Session = Depends(get_db)
):
    try:
        return DatasetImporter.import_harvest_lots(db, lots)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@router.post("/load-local-agmarknet")
def load_local_agmarknet_dataset(
    filepath: Optional[str] = "datasets/9ef84268-d588-465a-a308-a864a43d0070.csv",
    db: Session = Depends(get_db)
):
    """
    Ingests live regional AGMARKNET records from datasets/ into the active database.
    """
    return DatasetImporter.load_local_agmarknet_csv(db, filepath or "datasets/9ef84268-d588-465a-a308-a864a43d0070.csv")

@router.get("/mandi-prices")
def get_mandi_prices(
    commodity: Optional[str] = None,
    market: Optional[str] = None,
    date: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(MandiPriceRecord)
    if commodity:
        query = query.filter(MandiPriceRecord.commodity.ilike(f"%{commodity}%"))
    if market:
        query = query.filter(MandiPriceRecord.market_name.ilike(f"%{market}%"))
    if date:
        query = query.filter(MandiPriceRecord.arrival_date == date)

    records = query.all()
    return {
        "success": True,
        "count": len(records),
        "data": [
            {
                "id": r.id,
                "market": r.market_name,
                "commodity": r.commodity,
                "variety": r.variety,
                "grade": r.grade,
                "date": r.arrival_date,
                "arrivals_tonnes": r.arrivals_tonnes,
                "modal_price_per_kg": r.modal_price_per_kg,
                "min_price_per_quintal": r.min_price_per_quintal,
                "max_price_per_quintal": r.max_price_per_quintal,
                "source": r.source
            }
            for r in records
        ]
    }

@router.get("/summary")
def get_dataset_summary(db: Session = Depends(get_db)):
    return {
        "success": True,
        "summary": DatasetImporter.get_summary(db)
    }
