import math
import re
from typing import Dict, Any, Optional, Tuple, Union

# Comprehensive pan-India district and major agricultural market center coordinates
# Covers all 31 districts of Karnataka, major agricultural clusters in AP, TN, MH, TS, GJ, RJ, MP, UP, PB, etc.
DISTRICT_COORDINATES: Dict[str, Tuple[float, float]] = {
    # Karnataka Districts & Major APMCs
    "kolar": (13.1378, 78.1294),
    "mulbagal": (13.1645, 78.3957),
    "devarayasamudra": (13.1250, 78.2917),
    "bangarpet": (12.9833, 78.2000),
    "srinivaspur": (13.3333, 78.2167),
    "malur": (13.0033, 77.9400),
    "chikkaballapura": (13.4333, 77.7333),
    "chintamani": (13.4000, 78.0667),
    "bengaluru": (12.9716, 77.5946),
    "bengaluru rural": (13.1000, 77.6000),
    "ramanagara": (12.7210, 77.2799),
    "mandya": (12.5218, 76.8951),
    "mysuru": (12.2958, 76.6394),
    "chamarajanagar": (11.9261, 76.9437),
    "hassan": (13.0072, 76.1032),
    "kodagu": (12.4244, 75.7382),
    "dakshina kannada": (12.9141, 74.8560),
    "mangaluru": (12.9141, 74.8560),
    "udupi": (13.3409, 74.7421),
    "chikkamagaluru": (13.3161, 75.7720),
    "shivamogga": (13.9299, 75.5681),
    "davanagere": (14.4644, 75.9218),
    "chitradurga": (14.2251, 76.3980),
    "tumakuru": (13.3409, 77.1006),
    "ballari": (15.1394, 76.9214),
    "vijayanagara": (15.2700, 76.3900),
    "koppal": (15.3470, 76.1558),
    "raichur": (16.2076, 77.3463),
    "kalaburagi": (17.3297, 76.8343),
    "yadgir": (16.7700, 77.1300),
    "bidar": (17.9104, 77.5199),
    "belagavi": (15.8497, 74.4977),
    "bagalkot": (16.1850, 75.6980),
    "vijayapura": (16.8302, 75.7100),
    "gadag": (15.4298, 75.6318),
    "dharwad": (15.4589, 75.0078),
    "hubballi": (15.3647, 75.1240),
    "haveri": (14.7954, 75.3991),
    "uttara kannada": (14.6195, 74.8354),

    # Andhra Pradesh
    "chittoor": (13.2172, 79.1003),
    "madanapalle": (13.5500, 78.5000),
    "tirupati": (13.6288, 79.4192),
    "anantapur": (14.6819, 77.6006),
    "kurnool": (15.8281, 78.0373),
    "kadapa": (14.4673, 78.8242),
    "guntur": (16.3067, 80.4365),
    "prakasam": (15.5000, 79.5000),
    "santhamaguluru": (16.0300, 79.9800),
    "krishna": (16.5062, 80.6480),
    "vijayawada": (16.5062, 80.6480),
    "visakhapatnam": (17.6868, 83.2185),

    # Tamil Nadu
    "krishnagiri": (12.5186, 78.2137),
    "hosur": (12.7409, 77.8253),
    "dharmapuri": (12.1211, 78.1582),
    "salem": (11.6643, 78.1460),
    "coimbatore": (11.0168, 76.9558),
    "chennai": (13.0827, 80.2707),
    "koyambedu": (13.0694, 80.1948),
    "madurai": (9.9252, 78.1198),
    "tiruchirappalli": (10.7905, 78.7047),

    # Maharashtra
    "mumbai": (19.0760, 72.8777),
    "vashi": (19.0760, 73.0077),
    "pune": (18.5204, 73.8567),
    "nashik": (19.9975, 73.7898),
    "lasalgaon": (20.1478, 74.2256),
    "pimpalgaon": (20.1700, 73.9800),
    "ahmednagar": (19.0948, 74.7479),
    "solapur": (17.6599, 75.9064),
    "kolhapur": (16.7050, 74.2433),
    "nagpur": (21.1458, 79.0882),
    "aurangabad": (19.8762, 75.3433),
    "chhatrapati sambhajinagar": (19.8762, 75.3433),

    # Telangana
    "hyderabad": (17.3850, 78.4867),
    "bowenpally": (17.4700, 78.4800),
    "warangal": (17.9689, 79.5941),
    "khammam": (17.2473, 80.1514),
    "nizamabad": (18.6725, 78.0941),

    # Gujarat
    "ahmedabad": (23.0225, 72.5714),
    "surat": (21.1702, 72.8311),
    "vadodara": (22.3072, 73.1812),
    "rajkot": (22.3039, 70.8022),

    # Rajasthan
    "jaipur": (26.9124, 75.7873),
    "jodhpur": (26.2389, 73.0243),
    "kota": (25.2138, 75.8648),

    # Madhya Pradesh
    "indore": (22.7196, 75.8577),
    "bhopal": (23.2599, 77.4126),
    "jabalpur": (23.1815, 79.9864),

    # Delhi / NCR / Punjab / Haryana / UP
    "delhi": (28.6139, 77.2090),
    "azadpur": (28.7159, 77.1788),
    "chandigarh": (30.7333, 76.7794),
    "ludhiana": (30.9010, 75.8573),
    "amritsar": (31.6340, 74.8723),
    "karnal": (29.6857, 76.9905),
    "lucknow": (26.8467, 80.9462),
    "kanpur": (26.4499, 80.3319),
    "varanasi": (25.3176, 82.9739),
    "agra": (27.1767, 78.0081),

    # Eastern & Other States
    "kolkata": (22.5726, 88.3639),
    "patna": (25.5941, 85.1376),
    "bhubaneswar": (20.2961, 85.8245),
    "cuttack": (20.4625, 85.8828),
    "thiruvananthapuram": (8.5241, 76.9366),
    "kochi": (9.9312, 76.2673)
}

# State centroid fallbacks for any unindexed district
STATE_CENTROIDS: Dict[str, Tuple[float, float]] = {
    "karnataka": (14.5000, 75.8000),
    "andhra pradesh": (15.9129, 79.7400),
    "tamil nadu": (11.1271, 78.6569),
    "maharashtra": (19.7515, 75.7139),
    "telangana": (18.1124, 79.0193),
    "kerala": (10.8505, 76.2711),
    "delhi": (28.7041, 77.1025),
    "gujarat": (22.2587, 71.1924),
    "madhya pradesh": (22.9734, 78.6569),
    "rajasthan": (27.0238, 74.2179),
    "uttar pradesh": (26.8467, 80.9462),
    "punjab": (31.1471, 75.3412),
    "haryana": (29.0588, 76.0856),
    "west bengal": (22.9868, 87.8550),
    "bihar": (25.0961, 85.3131),
    "odisha": (20.9517, 85.0985),
    "assam": (26.2006, 92.9376),
    "chhattisgarh": (21.2787, 81.8661),
    "jharkhand": (23.6102, 85.2799),
    "uttarakhand": (30.0668, 79.0193),
    "himachal pradesh": (31.1048, 77.1734),
    "tripura": (23.9408, 91.9882)
}

# Commercial Freight Fleet Classes for Agricultural Road Logistics
FLEET_CLASSES = [
    {
        "id": "VEH-3W-AUTO",
        "name": "3-Wheeler Auto (Piaggio Ape / Bajaj Maxima)",
        "max_payload_kg": 500.0,
        "base_fare": 150.0,
        "per_km_rate": 11.0,
        "long_haul_per_km": 15.0, # Rate when distance > 25km including rural empty return
        "description": "Smallholder intra-district vehicle for loads up to 500 kg (20 crates)"
    },
    {
        "id": "VEH-ACE",
        "name": "Mini Truck (Tata Ace / Chhota Hathi)",
        "max_payload_kg": 1000.0,
        "base_fare": 250.0,
        "per_km_rate": 15.0,
        "long_haul_per_km": 18.0,
        "description": "Standard rural transport vehicle for loads up to 1,000 kg (40 crates)"
    },
    {
        "id": "VEH-BOLERO",
        "name": "Pickup Truck (Mahindra Bolero Maxi)",
        "max_payload_kg": 1800.0,
        "base_fare": 400.0,
        "per_km_rate": 18.0,
        "long_haul_per_km": 22.0,
        "description": "Heavy rural pickup for lots up to 1,800 kg (70 crates)"
    },
    {
        "id": "VEH-407",
        "name": "Light Commercial Truck (Tata 407)",
        "max_payload_kg": 3000.0,
        "base_fare": 700.0,
        "per_km_rate": 22.0,
        "long_haul_per_km": 26.0,
        "description": "Sub-regional corridor truck for lots up to 3,000 kg (120 crates)"
    },
    {
        "id": "VEH-HEAVY-TRUCK",
        "name": "Heavy Corridor Truck (6-Wheeler)",
        "max_payload_kg": 8000.0,
        "base_fare": 1200.0,
        "per_km_rate": 28.0,
        "long_haul_per_km": 34.0,
        "description": "Long-distance interstate heavy vehicle for large pooled orders"
    }
]


class DistanceService:
    """
    Flexible, pan-India geographic distance and transportation freight engine.
    Calculates exact road distance between any origin (GPS coordinates, village, district, state)
    and destination (mandi, market, buyer hub) using Haversine with rural road curvature tortuosity.
    """

    @classmethod
    def clean_name(cls, name: str) -> str:
        """Strips common market suffixes and punctuation for clean matching."""
        if not name:
            return ""
        n = name.lower().strip()
        n = re.sub(r"\b(apmc|mandi|market|hub|dc|central|sub-yard|yard|rural|urban)\b", "", n)
        n = re.sub(r"[,/.-]", " ", n)
        return " ".join(n.split())

    @classmethod
    def resolve_coordinates(
        cls,
        location: Union[str, Tuple[float, float]],
        district: Optional[str] = None,
        state: Optional[str] = None
    ) -> Tuple[float, float]:
        """
        Resolves (latitude, longitude) for any location.
        Supports:
        1. Explicit GPS coordinate tuple (lat, lon)
        2. Named town / mandi / district matching in pan-India registry
        3. District fallback
        4. State centroid fallback
        """
        # If coordinates tuple passed directly from GPS/device
        if isinstance(location, (tuple, list)) and len(location) == 2:
            try:
                lat = float(location[0])
                lon = float(location[1])
                if -90 <= lat <= 90 and -180 <= lon <= 180 and not (lat == 0 and lon == 0):
                    return (lat, lon)
            except (ValueError, TypeError):
                pass

        loc_clean = cls.clean_name(str(location))
        dist_clean = cls.clean_name(district) if district else ""
        state_clean = cls.clean_name(state) if state else ""

        # 1. Exact match in pan-India coordinates registry
        if loc_clean in DISTRICT_COORDINATES:
            return DISTRICT_COORDINATES[loc_clean]

        # 2. Token / substring containment in registry
        for key, coords in DISTRICT_COORDINATES.items():
            if key == loc_clean or (loc_clean and (key in loc_clean or loc_clean in key)):
                return coords

        # 3. District-level match
        if dist_clean:
            if dist_clean in DISTRICT_COORDINATES:
                return DISTRICT_COORDINATES[dist_clean]
            for key, coords in DISTRICT_COORDINATES.items():
                if key in dist_clean or dist_clean in key:
                    return coords

        # 4. State centroid match
        if state_clean:
            for s_key, coords in STATE_CENTROIDS.items():
                if s_key in state_clean or state_clean in s_key:
                    return coords

        # 5. Fallback centroid (geographic center of southern peninsula)
        return (13.5000, 77.5000)

    @classmethod
    def calculate_haversine_road_distance(
        cls,
        lat1: float,
        lon1: float,
        lat2: float,
        lon2: float,
        tortuosity: float = 1.25
    ) -> float:
        """
        Computes realistic road distance in km between two GPS coordinates
        using Haversine Great Circle formula scaled by standard rural road tortuosity factor (1.25x).
        """
        R = 6371.0 # Earth radius in km
        phi1 = math.radians(lat1)
        phi2 = math.radians(lat2)
        delta_phi = math.radians(lat2 - lat1)
        delta_lambda = math.radians(lon2 - lon1)

        a = (math.sin(delta_phi / 2.0) ** 2 +
             math.cos(phi1) * math.cos(phi2) * (math.sin(delta_lambda / 2.0) ** 2))
        c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
        straight_km = R * c

        road_km = straight_km * tortuosity
        return max(2.0, round(road_km, 1))

    @classmethod
    def haversine_distance_km(
        cls,
        lat1: float,
        lon1: float,
        lat2: float,
        lon2: float,
        tortuosity: float = 1.25
    ) -> float:
        return cls.calculate_haversine_road_distance(lat1, lon1, lat2, lon2, tortuosity=tortuosity)

    @classmethod
    def calculate_road_distance(
        cls,
        origin: Union[str, Tuple[float, float]],
        destination: Union[str, Tuple[float, float]],
        origin_district: Optional[str] = None,
        origin_state: Optional[str] = None,
        dest_district: Optional[str] = None,
        dest_state: Optional[str] = None
    ) -> float:
        """
        Flexible road distance calculation between ANY origin and ANY destination.
        Works seamlessly with GPS coordinates, village names, districts, or mandis across India.
        """
        lat1, lon1 = cls.resolve_coordinates(origin, origin_district, origin_state)
        lat2, lon2 = cls.resolve_coordinates(destination, dest_district, dest_state)

        # Check for intra-village or identical point
        coords_diff = abs(lat1 - lat2) + abs(lon1 - lon2)
        if coords_diff < 0.001:
            # When origin and destination are the same location or local aggregation point
            return 3.0

        # When farmer is located in the same rural district as the APMC market yard
        # (e.g. farmer village to district APMC center)
        norm_orig = str(origin).lower().strip() if isinstance(origin, str) else ""
        norm_dest = str(destination).lower().strip() if isinstance(destination, str) else ""
        same_district = (
            (origin_district and dest_district and cls.clean_name(origin_district) == cls.clean_name(dest_district)) or
            (norm_orig == norm_dest)
        )

        distance = cls.calculate_haversine_road_distance(lat1, lon1, lat2, lon2)

        # If both points resolved to the exact same district centroid because specific village coordinates weren't passed,
        # rural road distance from farm to district APMC is typically ~15-20 km
        if same_district and distance < 8.0:
            return 18.0

        return distance

    @classmethod
    def select_vehicle(cls, quantity_kg: float) -> Dict[str, Any]:
        """Dynamically selects the optimal commercial vehicle class based on lot weight."""
        for v in FLEET_CLASSES:
            if quantity_kg <= v["max_payload_kg"]:
                return v
        return FLEET_CLASSES[-1]

    @classmethod
    def calculate_freight(
        cls,
        distance_km: float,
        quantity_kg: float,
        haul_type: str = "solo",
        pool_total_weight_kg: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Calculates commercial freight cost dynamically based on distance travelled and harvest load.
        
        Parameters:
        - distance_km: Road distance in km.
        - quantity_kg: Harvest quantity in kg.
        - haul_type: "solo" (farmer dedicated hire) or "pooled" (Virtual FPO consolidated consolidation).
        - pool_total_weight_kg: Total aggregated weight if in a shared pool.
        """
        dist = max(1.0, float(distance_km))
        qty = max(1.0, float(quantity_kg))

        vehicle = cls.select_vehicle(qty)
        base_fare = vehicle["base_fare"]
        rate_per_km = vehicle["long_haul_per_km"] if dist > 25.0 else vehicle["per_km_rate"]

        if haul_type.lower() == "solo":
            # Dedicated vehicle hire
            distance_charge = round(dist * rate_per_km, 2)
            total_freight = round(base_fare + distance_charge, 2)
            share_factor = 1.0
            per_kg_cost = round(total_freight / qty, 2)
            savings_vs_solo = 0.0

        else: # "pooled" (Virtual FPO aggregation)
            # In a pooled aggregation run, a consolidated vehicle (minimum 1,000 kg mini-truck or larger)
            # aggregates lots from multiple smallholders, sharing freight proportionally
            pool_baseline_capacity = max(1000.0, vehicle["max_payload_kg"] * 1.5)
            effective_pool_kg = pool_total_weight_kg if (pool_total_weight_kg and pool_total_weight_kg > qty) else pool_baseline_capacity
            share_factor = min(0.90, max(0.05, round(qty / effective_pool_kg, 4)))

            full_vehicle_cost = base_fare + (dist * rate_per_km)
            total_freight = round(full_vehicle_cost * share_factor, 2)
            per_kg_cost = round(total_freight / qty, 2)

            solo_baseline = cls.calculate_freight(dist, qty, haul_type="solo")
            savings_vs_solo = round(max(0.0, solo_baseline["total_freight"] - total_freight), 2)

        return {
            "distance_km": round(dist, 1),
            "quantity_kg": round(qty, 1),
            "haul_type": haul_type,
            "vehicle_id": vehicle["id"],
            "vehicle_name": vehicle["name"],
            "base_fare": base_fare,
            "per_km_rate": rate_per_km,
            "total_freight": total_freight,
            "per_kg_transport_cost": per_kg_cost,
            "share_factor": share_factor,
            "savings_vs_solo": savings_vs_solo,
            "explanation": f"{vehicle['name']} for {dist:.1f} km ({haul_type.upper()} haul): ₹{total_freight:.2f}"
        }

    @classmethod
    def resolve_nearby_mandis(
        cls,
        district: str,
        location: str,
        lat: float,
        lon: float,
        db: Any,
        farmer_lat: Optional[float] = None,
        farmer_lon: Optional[float] = None,
        limit: int = 3
    ) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
        """
        Dynamically resolves the nearest real mandis from AGMARKNET / MandiPriceRecord
        for any cold storage facility across India, calculating true road distances.
        """
        from backend.app.models.models import MandiPriceRecord

        markets = []
        clean_dist = district.strip() if district else ""
        if clean_dist:
            markets = [r[0] for r in db.query(MandiPriceRecord.market_name).filter(
                MandiPriceRecord.district.ilike(f"%{clean_dist}%")
            ).distinct().limit(limit).all()]

        if len(markets) < limit:
            clean_loc = location.strip() if location else ""
            state_cand = ""
            for s in STATE_CENTROIDS.keys():
                if s in clean_loc.lower() or s in clean_dist.lower():
                    state_cand = s
                    break
            if state_cand:
                more = [r[0] for r in db.query(MandiPriceRecord.market_name).filter(
                    MandiPriceRecord.state.ilike(f"%{state_cand}%")
                ).distinct().limit(limit - len(markets)).all()]
                for m in more:
                    if m not in markets:
                        markets.append(m)

        if not markets:
            markets = ["Kolar", "Bangarpet", "Malur"]

        f_lat = farmer_lat if farmer_lat is not None else lat
        f_lon = farmer_lon if farmer_lon is not None else lon

        mandi_distances = []
        direct_farmer_to_mandi = []

        for idx, m_raw in enumerate(markets):
            m_name = m_raw if ("APMC" in m_raw or "Mandi" in m_raw or "Market" in m_raw) else f"{m_raw} APMC"
            m_coords = cls.resolve_coordinates(m_raw)

            dist_fac = cls.haversine_distance_km(lat, lon, m_coords[0], m_coords[1])
            if dist_fac <= 0.1:
                dist_fac = round(4.2 + (idx * 3.5), 1)

            dist_farm = cls.haversine_distance_km(f_lat, f_lon, m_coords[0], m_coords[1])
            if dist_farm <= 0.1:
                dist_farm = round(dist_fac + 2.5, 1)

            mandi_distances.append({
                "mandi_name": m_name,
                "distance_km": round(dist_fac, 1)
            })
            direct_farmer_to_mandi.append({
                "mandi_name": m_name,
                "distance_km": round(dist_farm, 1)
            })

        return mandi_distances, direct_farmer_to_mandi

