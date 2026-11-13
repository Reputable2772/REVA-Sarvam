import json
import csv
import os
from typing import List, Dict, Any, Optional

class AgmarknetImporter:
    def __init__(self, seed_dir: Optional[str] = None):
        if seed_dir is None:
            seed_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data", "seed")
        self.seed_dir = seed_dir
        self.mandis: List[Dict[str, Any]] = []

    def import_from_json(self, file_path: Optional[str] = None) -> List[Dict[str, Any]]:
        if file_path is None:
            file_path = os.path.join(self.seed_dir, "agmarknet_kolar_region_prices.json")
        
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            self.mandis = data.get("records", [])
        return self.mandis

    def import_from_csv(self, file_path: Optional[str] = None) -> List[Dict[str, Any]]:
        if file_path is None:
            file_path = os.path.join(self.seed_dir, "agmarknet_kolar_region_prices.csv")
            
        records = []
        with open(file_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                records.append({
                    "mandi_name": row["mandi_name"],
                    "distance_from_mulbagal_km": float(row["distance_km"]),
                    "modal_price_per_kg": float(row["modal_price_per_kg"]),
                    "min_price_per_kg": float(row["min_price_per_kg"]),
                    "max_price_per_kg": float(row["max_price_per_kg"]),
                    "daily_arrivals_tonnes": float(row["daily_arrivals_tonnes"]),
                    "commission_pct": float(row["commission_pct"]),
                    "unloading_fee_per_crate": float(row["unloading_crate_fee"]),
                    "weighment_fee_per_lot": float(row["weighment_fee"]),
                    "estimated_transport_per_farmer": float(row["transport_cost"]),
                    "is_trap": row.get("is_trap", "False").lower() in ["true", "1"],
                    "trap_reason": row.get("trap_reason", "")
                })
        self.mandis = records
        return self.mandis

    def evaluate_mandi_netbacks(self, quantity_kg: float = 100.0) -> List[Dict[str, Any]]:
        if not self.mandis:
            self.import_from_json()

        results = []
        # Standard tomato crate = ~25kg
        crates = max(1, round(quantity_kg / 25.0))

        for m in self.mandis:
            modal = m["modal_price_per_kg"]
            gross = quantity_kg * modal
            commission = round(gross * (m["commission_pct"] / 100.0), 2)
            unloading = crates * m["unloading_fee_per_crate"]
            weighing = m["weighment_fee_per_lot"]
            transport = m["estimated_transport_per_farmer"]
            
            total_deductions = round(commission + unloading + weighing + transport, 2)
            net_take_home = round(gross - total_deductions, 2)
            effective_rate = round(net_take_home / quantity_kg, 2)

            results.append({
                "mandi_name": m["mandi_name"],
                "distance_km": m["distance_from_mulbagal_km"],
                "modal_price_per_kg": modal,
                "gross_revenue": gross,
                "deductions": {
                    "commission": commission,
                    "unloading": unloading,
                    "weighing": weighing,
                    "transport": transport,
                    "total": total_deductions
                },
                "net_take_home": net_take_home,
                "effective_rate_per_kg": effective_rate,
                "is_trap": m.get("is_trap", False),
                "trap_reason": m.get("trap_reason", "")
            })

        # Rank by net take-home descending
        results.sort(key=lambda x: x["net_take_home"], reverse=True)
        return results
