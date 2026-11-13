import os
import csv
import math
from typing import List, Dict, Any, Optional, Tuple

class PanIndiaMandiService:
    """
    Pan-India Mandi Engine & Arbitrage Calculator.
    Indexes the 13,073-row national AGMARKNET daily arrivals & prices dataset:
    `datasets/9ef84268-d588-465a-a308-a864a43d0070.csv`.
    Supports querying any Mandi across all 26+ states and calculating transparent deductions:
    - Transport freight (distance & weight scaled)
    - APMC statutory commission
    - Unloading & handling
    - Electronic weighing fee
    - Net Take-Home vs Headline Price Trap detection
    """

    _instance = None
    _dataset_records: List[Dict[str, Any]] = []

    def __init__(self, csv_path: Optional[str] = None):
        if csv_path is None:
            # Look in datasets/ in project root
            base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
            csv_path = os.path.join(base_dir, "datasets", "9ef84268-d588-465a-a308-a864a43d0070.csv")
            if not os.path.exists(csv_path):
                # Fallback to local datasets directory relative to cwd
                alt_path = os.path.join(os.getcwd(), "datasets", "9ef84268-d588-465a-a308-a864a43d0070.csv")
                if os.path.exists(alt_path):
                    csv_path = alt_path
        self.csv_path = csv_path
        self._load_dataset()

    def _load_dataset(self):
        if self._dataset_records:
            return

        if not os.path.exists(self.csv_path):
            return

        records = []
        with open(self.csv_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    modal_p = float(row.get("Modal_x0020_Price") or 0.0)
                    min_p = float(row.get("Min_x0020_Price") or 0.0)
                    max_p = float(row.get("Max_x0020_Price") or 0.0)
                except (ValueError, TypeError):
                    modal_p, min_p, max_p = 0.0, 0.0, 0.0

                records.append({
                    "state": row.get("State", "").strip(),
                    "district": row.get("District", "").strip(),
                    "market": row.get("Market", "").strip(),
                    "commodity": row.get("Commodity", "").strip(),
                    "variety": row.get("Variety", "").strip(),
                    "grade": row.get("Grade", "").strip(),
                    "arrival_date": row.get("Arrival_Date", "").strip(),
                    "min_price_quintal": min_p,
                    "max_price_quintal": max_p,
                    "modal_price_quintal": modal_p,
                    # Prices in dataset are ₹/quintal -> divide by 100 for ₹/kg
                    "modal_price_kg": round(modal_p / 100.0, 2),
                    "min_price_kg": round(min_p / 100.0, 2),
                    "max_price_kg": round(max_p / 100.0, 2),
                })
        self._dataset_records = records

    @classmethod
    def get_instance(cls) -> "PanIndiaMandiService":
        if cls._instance is None:
            cls._instance = PanIndiaMandiService()
        return cls._instance

    def get_all_states(self) -> List[str]:
        states = sorted(list({r["state"] for r in self._dataset_records if r["state"]}))
        return states

    def get_districts_by_state(self, state: str) -> List[str]:
        districts = sorted(list({
            r["district"] for r in self._dataset_records 
            if r["state"].lower() == state.lower() and r["district"]
        }))
        return districts

    def get_commodities(self, state: Optional[str] = None) -> List[str]:
        if state:
            commodities = sorted(list({
                r["commodity"] for r in self._dataset_records
                if r["state"].lower() == state.lower() and r["commodity"]
            }))
        else:
            commodities = sorted(list({r["commodity"] for r in self._dataset_records if r["commodity"]}))
        return commodities

    def search_mandis(
        self,
        commodity: Optional[str] = None,
        state: Optional[str] = None,
        district: Optional[str] = None,
        query: Optional[str] = None,
        limit: int = 50
    ) -> List[Dict[str, Any]]:
        """
        Search mandis across India by commodity, state, district, or keyword query.
        """
        results = []
        c_query = commodity.lower().strip() if commodity else ""
        s_query = state.lower().strip() if state else ""
        d_query = district.lower().strip() if district else ""
        q_query = query.lower().strip() if query else ""

        for r in self._dataset_records:
            if c_query and c_query not in r["commodity"].lower():
                continue
            if s_query and s_query != r["state"].lower():
                continue
            if d_query and d_query != r["district"].lower():
                continue
            if q_query:
                combined = f"{r['market']} {r['district']} {r['state']} {r['commodity']}".lower()
                if q_query not in combined:
                    continue
            results.append(r)
            if len(results) >= limit:
                break

        return results

    def _estimate_distance(
        self,
        origin_state: str,
        origin_district: str,
        dest_state: str,
        dest_district: str,
        dest_market: str,
        origin_village: Optional[str] = None,
        origin_coords: Optional[Tuple[float, float]] = None
    ) -> float:
        """
        Estimates realistic road distance in km between origin and destination mandi using DistanceService.
        """
        from backend.app.services.distance_service import DistanceService
        origin_point = origin_coords or origin_village or origin_district or origin_state or "Karnataka"
        return DistanceService.calculate_road_distance(
            origin=origin_point,
            destination=dest_market,
            origin_district=origin_district,
            origin_state=origin_state,
            dest_district=dest_district,
            dest_state=dest_state
        )

    def evaluate_pan_india_mandi_netbacks(
        self,
        commodity: str = "Tomato",
        quantity_kg: float = 800.0,
        origin_state: str = "Karnataka",
        origin_district: Optional[str] = None,
        candidate_limit: int = 10
    ) -> Dict[str, Any]:
        """
        Calculates itemized deductions and final net take-home for mandis trading the specified commodity.
        Identifies Headline Price Traps where higher gross rate yields lower take-home due to freight and deductions.
        """
        # Find mandis matching the commodity
        matching = []
        for r in self._dataset_records:
            if commodity.lower() in r["commodity"].lower():
                matching.append(r)

        if not matching:
            # Fallback to all mandis in state or all
            matching = [r for r in self._dataset_records if r["state"].lower() == origin_state.lower()]
            if not matching:
                matching = self._dataset_records[:candidate_limit]

        # Prioritize mandis:
        # 1. Same state & district
        # 2. Same state
        # 3. Other major states
        orig_dist_clean = (origin_district or "").strip().lower()
        orig_state_clean = (origin_state or "").strip().lower()

        def mandi_sort_key(m):
            score = 0
            if orig_state_clean and m["state"].lower() == orig_state_clean:
                score += 100
                if orig_dist_clean and m["district"].lower() == orig_dist_clean:
                    score += 50
            return score

        matching.sort(key=mandi_sort_key, reverse=True)

        # Pick diverse sample of candidates: regional + nearby + high modal price
        candidates: List[Dict[str, Any]] = []
        seen_markets = set()

        for m in matching:
            market_key = f"{m['market']}-{m['state']}"
            if market_key not in seen_markets:
                seen_markets.add(market_key)
                candidates.append(m)
                if len(candidates) >= candidate_limit:
                    break

        crates = max(1, math.ceil(quantity_kg / 25.0))
        from backend.app.services.distance_service import DistanceService

        evaluated = []
        for m in candidates:
            distance_km = self._estimate_distance(
                origin_state, origin_district, m["state"], m["district"], m["market"]
            )
            modal_kg = m["modal_price_kg"]
            gross_revenue = round(quantity_kg * modal_kg, 2)

            # Accurate freight transport calculation via DistanceService
            freight_info = DistanceService.calculate_freight(distance_km, quantity_kg, haul_type="solo")
            transport_cost = freight_info["total_freight"]

            # APMC commission: standard 1.5% - 2.0%
            comm_pct = 1.5 if "karnataka" in m["state"].lower() else 2.0
            commission = round(gross_revenue * (comm_pct / 100.0), 2)

            # Unloading charge: ₹5 per crate (~₹0.20/kg)
            unloading = round(crates * 5.0, 2)

            # Electronic weighment fee
            weighment = 40.0

            # Transit degradation & perishability shrinkage over long hauls
            is_perishable = any(p in commodity.lower() for p in ["tomato", "vegetable", "fruit", "onion", "potato", "chilli", "brinjal", "capsicum", "cabbage", "cauliflower"])
            if is_perishable:
                transit_loss_pct = 2.0 if distance_km <= 20.0 else min(20.0, round(2.0 + 2.5 * ((distance_km - 20.0) / 50.0), 1))
            else:
                transit_loss_pct = 0.5 if distance_km <= 50.0 else min(5.0, round(0.5 + 0.5 * ((distance_km - 50.0) / 100.0), 1))
            transit_loss = round(gross_revenue * (transit_loss_pct / 100.0), 2)

            total_deductions = round(transport_cost + commission + unloading + weighment + transit_loss, 2)
            net_take_home = round(gross_revenue - total_deductions, 2)
            effective_rate = round(net_take_home / quantity_kg, 2) if quantity_kg > 0 else 0.0

            evaluated.append({
                "market": m["market"],
                "district": m["district"],
                "state": m["state"],
                "commodity": m["commodity"],
                "variety": m["variety"],
                "distance_km": round(distance_km, 1),
                "transport_vehicle": freight_info["vehicle_name"],
                "modal_price_kg": modal_kg,
                "gross_revenue": gross_revenue,
                "deductions": {
                    "transport": transport_cost,
                    "commission": commission,
                    "commission_pct": comm_pct,
                    "unloading": unloading,
                    "weighment": weighment,
                    "transit_loss": transit_loss,
                    "transit_loss_pct": transit_loss_pct,
                    "total": total_deductions
                },
                "net_take_home": net_take_home,
                "effective_rate_per_kg": effective_rate,
                "is_trap": False,
                "trap_reason": ""
            })

        # Rank by net take-home
        evaluated.sort(key=lambda x: x["net_take_home"], reverse=True)

        if evaluated:
            best_net = evaluated[0]
            # Flag price traps dynamically: higher gross/modal price than best net option, but lower final net take-home
            for item in evaluated:
                if item["modal_price_kg"] > best_net["modal_price_kg"] and item["net_take_home"] < best_net["net_take_home"]:
                    diff = round(best_net["net_take_home"] - item["net_take_home"], 2)
                    item["is_trap"] = True
                    item["trap_reason"] = (
                        f"Headline Price Trap! ₹{item['modal_price_kg']}/kg appears higher, "
                        f"but high freight (₹{item['deductions']['transport']} over {item['distance_km']}km) leaves you with ₹{diff:.0f} LESS net take-home "
                        f"than {best_net['market']}."
                    )

        return {
            "commodity": commodity,
            "quantity_kg": quantity_kg,
            "origin_state": origin_state,
            "origin_district": origin_district,
            "mandis_analyzed_count": len(evaluated),
            "recommendation": evaluated[0]["market"] if evaluated else "None",
            "mandis": evaluated
        }

    @classmethod
    def evaluate_mandi_options(
        cls,
        crop: str = "Tomato",
        quantity_kg: float = 200.0,
        farmer_district: str = "Kolar",
        farmer_state: str = "Karnataka",
        target_mandi: Optional[str] = None,
        farmer_village: Optional[str] = None,
        farmer_coords: Optional[Tuple[float, float]] = None
    ) -> List[Dict[str, Any]]:
        """
        Evaluates transparent costs, itemized deductions, and net take-home for all relevant
        mandis trading the given crop, formatted for the Farmer Mobile & Voice Experience.
        Calculates exact road distance and transport freight from the farmer's location.
        """
        instance = cls.get_instance()
        records = instance._dataset_records
        crop_clean = crop.strip().lower()

        # Filter matching records
        matched = [r for r in records if crop_clean in r["commodity"].lower() or r["commodity"].lower() in crop_clean]
        if not matched:
            matched = [r for r in records if "tomato" in r["commodity"].lower()]

        # Fast Haversine pre-filtering across dataset to identify regional candidates
        from backend.app.services.distance_service import DistanceService

        origin_point = farmer_coords or farmer_village or farmer_district or farmer_state
        lat1, lon1 = DistanceService.resolve_coordinates(origin_point, farmer_district, farmer_state)

        candidates_with_dist = []
        seen_markets = set()
        for m in matched:
            market_key = f"{m['market']}-{m['state']}"
            if market_key in seen_markets:
                continue
            seen_markets.add(market_key)
            lat2, lon2 = DistanceService.resolve_coordinates(m["market"], m["district"], m["state"])
            d_approx = DistanceService.haversine_distance_km(lat1, lon1, lat2, lon2)
            candidates_with_dist.append((m, d_approx))

        # Sort candidates by approximate distance ascending
        candidates_with_dist.sort(key=lambda x: x[1])

        # Filter: realistic regional APMC catchment radius (<= 85 km)
        regional_candidates = [x for x in candidates_with_dist if x[1] <= 85.0]

        selected_candidates: List[Dict[str, Any]] = []
        if len(regional_candidates) >= 3:
            selected_candidates.extend([x[0] for x in regional_candidates[:4]])
        else:
            selected_candidates.extend([x[0] for x in candidates_with_dist[:4]])

        # If user explicitly searched for a specific target_mandi, always include it
        if target_mandi:
            t_clean = target_mandi.strip().lower()
            target_match = next((x[0] for x in candidates_with_dist if t_clean in x[0]["market"].lower()), None)
            if target_match and target_match not in selected_candidates:
                selected_candidates.append(target_match)

        # Include 1 distant APMC in the same state for comparison if available (e.g. highest headline price)
        # to demonstrate transparent netback arbitrage vs freight & transit degradation
        distant_state = [x for x in candidates_with_dist if x[0]["state"].lower() == farmer_state.lower() and x[1] > 85.0]
        if distant_state:
            distant_highest = max(distant_state, key=lambda x: x[0]["modal_price_kg"])[0]
            if distant_highest not in selected_candidates and len(selected_candidates) < 5:
                selected_candidates.append(distant_highest)

        crates = max(1, round(quantity_kg / 25.0))
        results = []

        # For the selected candidates, compute exact turn-by-turn road distance via OpenStreetMap OSRM
        for m in selected_candidates:
            market_name = m["market"]
            distance_km = instance._estimate_distance(
                farmer_state, farmer_district, m["state"], m["district"], market_name,
                origin_village=farmer_village,
                origin_coords=farmer_coords
            )

            modal_rate = m.get("modal_price_kg", 20.0)
            gross_revenue = round(quantity_kg * modal_rate, 2)

            # Accurate freight transport calculation via DistanceService
            freight_info = DistanceService.calculate_freight(distance_km, quantity_kg, haul_type="solo")
            transport_cost = freight_info["total_freight"]

            # Standard APMC commission
            comm_pct = 1.5 if "karnataka" in m["state"].lower() else 2.0
            commission_cost = round(gross_revenue * (comm_pct / 100.0), 2)

            unloading_cost = round(crates * 5.0, 2)
            weighing_fee = 30.0

            # Transit degradation & perishability shrinkage over long hauls
            is_perishable = any(p in crop_clean for p in ["tomato", "vegetable", "fruit", "onion", "potato", "chilli", "brinjal", "capsicum", "cabbage", "cauliflower"])
            if is_perishable:
                transit_loss_pct = 2.0 if distance_km <= 20.0 else min(20.0, round(2.0 + 2.5 * ((distance_km - 20.0) / 50.0), 1))
            else:
                transit_loss_pct = 0.5 if distance_km <= 50.0 else min(5.0, round(0.5 + 0.5 * ((distance_km - 50.0) / 100.0), 1))
            transit_loss_cost = round(gross_revenue * (transit_loss_pct / 100.0), 2)

            total_deductions = round(commission_cost + transport_cost + unloading_cost + weighing_fee + transit_loss_cost, 2)
            net_take_home = round(gross_revenue - total_deductions, 2)
            effective_rate_per_kg = round(net_take_home / quantity_kg, 2) if quantity_kg > 0 else 0.0

            results.append({
                "mandi_name": market_name,
                "district": m["district"],
                "state": m["state"],
                "commodity": m["commodity"],
                "distance_km": round(distance_km, 1),
                "transport_vehicle": freight_info["vehicle_name"],
                "modal_price_per_kg": modal_rate,
                "min_price_per_kg": m.get("min_price_kg", modal_rate - 2),
                "max_price_per_kg": m.get("max_price_kg", modal_rate + 2),
                "gross_revenue": gross_revenue,
                "itemized_deductions": {
                    "transport_cost": transport_cost,
                    "commission_cost": commission_cost,
                    "commission_pct": comm_pct,
                    "unloading_cost": unloading_cost,
                    "weighing_fee": weighing_fee,
                    "transit_loss_cost": transit_loss_cost,
                    "transit_loss_pct": transit_loss_pct,
                    "total_deductions": total_deductions
                },
                "net_take_home": net_take_home,
                "effective_rate_per_kg": effective_rate_per_kg,
                "is_trap": False,
                "trap_reason": ""
            })

        # Add Virtual FPO Direct Buyer Option as gold standard recommendation
        direct_rate = round(max([r["modal_price_per_kg"] for r in results] + [25.0]) + 0.50, 2)
        direct_gross = round(quantity_kg * direct_rate, 2)
        direct_freight_info = DistanceService.calculate_freight(5.0, quantity_kg, haul_type="pooled")
        shared_freight = direct_freight_info["total_freight"]
        direct_loading = round(crates * 2.0, 2)
        direct_deductions = round(shared_freight + direct_loading, 2)
        direct_net = round(direct_gross - direct_deductions, 2)

        results.append({
            "mandi_name": "Virtual FPO Direct Buyer (Aggregated Pool)",
            "district": farmer_district,
            "state": farmer_state,
            "commodity": crop.capitalize(),
            "distance_km": 5.0,
            "transport_vehicle": direct_freight_info["vehicle_name"],
            "modal_price_per_kg": direct_rate,
            "min_price_per_kg": direct_rate,
            "max_price_per_kg": direct_rate,
            "gross_revenue": direct_gross,
            "itemized_deductions": {
                "transport_cost": shared_freight,
                "commission_cost": 0.0,
                "commission_pct": 0.0,
                "unloading_cost": direct_loading,
                "weighing_fee": 0.0,
                "total_deductions": direct_deductions
            },
            "net_take_home": direct_net,
            "effective_rate_per_kg": round(direct_net / quantity_kg, 2),
            "is_trap": False,
            "trap_reason": "",
            "is_recommended": True
        })

        # Flag headline price traps: higher gross/modal price than a local mandi, but lower net take-home
        for r in results:
            for other in results:
                if other["mandi_name"] != r["mandi_name"] and not other.get("is_recommended", False):
                    if other["modal_price_per_kg"] > r["modal_price_per_kg"] and other["net_take_home"] < r["net_take_home"]:
                        diff_rate = round(other["modal_price_per_kg"] - r["modal_price_per_kg"], 2)
                        loss_net = round(r["net_take_home"] - other["net_take_home"], 2)
                        other["is_trap"] = True
                        other["trap_reason"] = (
                            f"Headline Price Trap: Gross price is ₹{diff_rate}/kg higher than {r['mandi_name']}, "
                            f"but higher freight ({other['distance_km']}km) and commission cuts result in "
                            f"₹{loss_net} LESS net take-home cash in your pocket!"
                        )

        # Sort results: recommended first, then by net take-home descending
        results.sort(key=lambda x: (not x.get("is_recommended", False), -x["net_take_home"]))
        return results
