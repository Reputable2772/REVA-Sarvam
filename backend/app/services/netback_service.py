from typing import Dict, Any, Optional, Tuple, Union
from backend.app.services.distance_service import DistanceService

class NetbackService:
    """
    Deterministic Netback Arbitrage Calculator.
    Calculates accurate road distance travelled and commercial vehicle freight costs dynamically
    for ANY farmer location and ANY candidate mandis across India.
    
    Compares 3 distinct selling channels:
    1. Local Mandi (nearest regional APMC)
    2. Distant / Alternate Mandi (often presenting headline price traps)
    3. Direct Institutional Buyer via Virtual FPO (aggregated load, zero middleman commission)
    """

    @staticmethod
    def calculate_comparison(
        quantity_kg: float,
        crop: str = "Tomato",
        kolar_rate: Optional[float] = None, # Legacy alias for local_rate
        bangarpet_rate: Optional[float] = None, # Legacy alias for distant_rate
        local_rate: Optional[float] = None,
        distant_rate: Optional[float] = None,
        direct_rate: float = 28.50,
        shared_freight_cost: Optional[float] = None,
        origin_location: Optional[Union[str, Tuple[float, float]]] = None,
        origin_district: Optional[str] = None,
        origin_state: Optional[str] = None,
        local_mandi_name: Optional[str] = None,
        local_mandi_location: Optional[Union[str, Tuple[float, float]]] = None,
        distant_mandi_name: Optional[str] = None,
        distant_mandi_location: Optional[Union[str, Tuple[float, float]]] = None,
        local_transport: Optional[float] = None,
        distant_transport: Optional[float] = None,
        kolar_transport: Optional[float] = None, # Legacy alias
        bangarpet_transport: Optional[float] = None # Legacy alias
    ) -> Dict[str, Any]:
        """
        Computes complete cost deductions, road distance travelled, and effective net return per kg
        across all three selling routes for any farmer and mandi.
        """
        # Resolve rates (supporting both generic and legacy parameter names)
        r_local = local_rate if local_rate is not None else (kolar_rate if kolar_rate is not None else 24.0)
        r_distant = distant_rate if distant_rate is not None else (bangarpet_rate if bangarpet_rate is not None else 28.0)
        r_direct = direct_rate if direct_rate is not None else 28.50

        # Resolve channel names
        name_local = local_mandi_name or "Local Mandi (Nearest APMC)"
        name_distant = distant_mandi_name or "Distant Mandi (Alternate Market)"
        name_direct = "Direct Buyer (Virtual FPO Aggregated Pool)"

        # Resolve destinations for distance calculation
        orig = origin_location or origin_district or "Local Hub"
        dest_local = local_mandi_location or local_mandi_name or "Local APMC"
        dest_distant = distant_mandi_location or distant_mandi_name or "Distant APMC"

        # Dynamically calculate road distances using DistanceService
        if local_mandi_location or origin_location:
            dist_local = DistanceService.calculate_road_distance(
                origin=orig,
                destination=dest_local,
                origin_district=origin_district,
                origin_state=origin_state
            )
        else:
            dist_local = 18.0 # Standard local APMC distance benchmark

        if distant_mandi_location or origin_location:
            dist_distant = DistanceService.calculate_road_distance(
                origin=orig,
                destination=dest_distant,
                origin_district=origin_district,
                origin_state=origin_state
            )
        else:
            dist_distant = 39.0 # Standard distant APMC benchmark

        dist_direct = 5.0 # Direct collection point is close to the village cluster

        # Compute dynamic commercial freight using DistanceService
        freight_local = DistanceService.calculate_freight(dist_local, quantity_kg, haul_type="solo")
        freight_distant = DistanceService.calculate_freight(dist_distant, quantity_kg, haul_type="solo")
        freight_direct = DistanceService.calculate_freight(dist_direct, quantity_kg, haul_type="pooled")

        # 1. Local Mandi
        gross_local = round(quantity_kg * r_local, 2)
        trans_local = local_transport or kolar_transport or freight_local["total_freight"]
        loading_local = 80.0
        comm_local = round(gross_local * 0.065, 2) # 6.5% APMC agent commission
        weigh_local = 30.0
        rejection_local = round(quantity_kg * 0.05 * r_local, 2) # 5% sorting & transit loss
        total_deductions_local = round(trans_local + loading_local + comm_local + weigh_local + rejection_local, 2)
        net_local = round(gross_local - total_deductions_local, 2)
        effective_local = round(net_local / quantity_kg, 2) if quantity_kg > 0 else 0.0

        # 2. Distant Mandi (Often presents Headline Price Trap)
        gross_distant = round(quantity_kg * r_distant, 2)
        trans_distant = distant_transport or bangarpet_transport or freight_distant["total_freight"]
        loading_distant = 100.0
        comm_distant = round(gross_distant * 0.05, 2) # 5.0% commission
        weigh_distant = 30.0
        rejection_distant = round(quantity_kg * 0.10 * r_distant, 2) # 10% transit damage on longer haul
        total_deductions_distant = round(trans_distant + loading_distant + comm_distant + weigh_distant + rejection_distant, 2)
        net_distant = round(gross_distant - total_deductions_distant, 2)
        effective_distant = round(net_distant / quantity_kg, 2) if quantity_kg > 0 else 0.0

        # 3. Direct Buyer via Virtual FPO
        gross_direct = round(quantity_kg * r_direct, 2)
        trans_direct = shared_freight_cost if shared_freight_cost is not None else freight_direct["total_freight"]
        loading_direct = 50.0
        comm_direct = 0.0 # Zero middleman commission
        platform_fee_direct = 30.0
        rejection_direct = 0.0 # Pre-graded zero loss
        total_deductions_direct = round(trans_direct + loading_direct + comm_direct + platform_fee_direct + rejection_direct, 2)
        net_direct = round(gross_direct - total_deductions_direct, 2)
        effective_direct = round(net_direct / quantity_kg, 2) if quantity_kg > 0 else 0.0

        # Net gains comparison
        gain_over_local = round(net_direct - net_local, 2)
        gain_pct_local = round((gain_over_local / net_local) * 100, 1) if net_local > 0 else 0.0

        gain_over_distant = round(net_direct - net_distant, 2)
        gain_pct_distant = round((gain_over_distant / net_distant) * 100, 1) if net_distant > 0 else 0.0

        # Headline trap: higher gross rate but lower net take-home due to distance & deductions
        headline_trap_detected = (r_distant > r_local) and (net_distant < net_local)

        return {
            "quantity_kg": quantity_kg,
            "crop": crop,
            "origin_location": str(orig),
            "channels": [
                {
                    "id": "local_mandi",
                    "name": name_local,
                    "headline_rate_per_kg": r_local,
                    "gross_revenue": gross_local,
                    "distance_km": dist_local,
                    "transport_vehicle": freight_local["vehicle_name"],
                    "deductions": {
                        "transport": trans_local,
                        "loading_unloading": loading_local,
                        "commission": comm_local,
                        "weighing_fee": weigh_local,
                        "rejection_loss": rejection_local
                    },
                    "total_costs": total_deductions_local,
                    "net_earnings": net_local,
                    "effective_rate_per_kg": effective_local
                },
                {
                    "id": "distant_mandi",
                    "name": name_distant,
                    "headline_rate_per_kg": r_distant,
                    "gross_revenue": gross_distant,
                    "distance_km": dist_distant,
                    "transport_vehicle": freight_distant["vehicle_name"],
                    "deductions": {
                        "transport": trans_distant,
                        "loading_unloading": loading_distant,
                        "commission": comm_distant,
                        "weighing_fee": weigh_distant,
                        "rejection_loss": rejection_distant
                    },
                    "total_costs": total_deductions_distant,
                    "net_earnings": net_distant,
                    "effective_rate_per_kg": effective_distant,
                    "is_headline_trap": headline_trap_detected,
                    "trap_warning": f"⚠️ Offers +₹{round(r_distant - r_local, 2)} headline price but yields LESS net cash due to {dist_distant}km transport (₹{trans_distant}) and transit loss!" if headline_trap_detected else None
                },
                {
                    "id": "virtual_fpo_direct",
                    "name": name_direct,
                    "headline_rate_per_kg": r_direct,
                    "gross_revenue": gross_direct,
                    "distance_km": dist_direct,
                    "transport_vehicle": freight_direct["vehicle_name"],
                    "deductions": {
                        "shared_freight": trans_direct,
                        "handling": loading_direct,
                        "commission": comm_direct,
                        "platform_fee": platform_fee_direct
                    },
                    "total_costs": total_deductions_direct,
                    "net_earnings": net_direct,
                    "effective_rate_per_kg": effective_direct,
                    "benefit_over_local_mandi": f"+₹{gain_over_local} (+{gain_pct_local}%)",
                    "benefit_over_distant_mandi": f"+₹{gain_over_distant} (+{gain_pct_distant}%)"
                }
            ],
            "recommendation": name_direct,
            "net_advantage_amount": gain_over_local,
            "net_advantage_pct": gain_pct_local,
            "headline_trap_detected": headline_trap_detected
        }
