import csv
import os
import re
from typing import List, Dict, Set, Tuple, Optional

class GeoDatasetService:
    """
    Agricultural Geographic Dataset Validation Engine
    Extracts official States, Districts, and Mandis from datasets/9ef84268-d588-465a-a308-a864a43d0070.csv
    and enforces strict spatial verification for farmer registration.
    """

    DATASET_PATH = "datasets/9ef84268-d588-465a-a308-a864a43d0070.csv"
    _STATES: Optional[List[str]] = None
    _STATE_DISTRICTS_MAP: Optional[Dict[str, Set[str]]] = None

    # Indian Postal Code Prefixes by State
    STATE_PINCODE_RANGES = {
        "Karnataka": ("560000", "599999"),
        "Andhra Pradesh": ("515000", "535999"),
        "Tamil Nadu": ("600000", "643999"),
        "Maharashtra": ("400000", "445999"),
        "Telangana": ("500000", "509999"),
        "Kerala": ("670000", "695999"),
        "Keralam": ("670000", "695999"),
        "Gujarat": ("360000", "396999"),
        "Rajasthan": ("301000", "345999"),
        "Uttar Pradesh": ("201000", "285999"),
        "Madhya Pradesh": ("450000", "488999"),
        "Punjab": ("140000", "160999"),
        "Haryana": ("121000", "136999"),
        "Odisha": ("751000", "770999"),
        "West Bengal": ("700000", "743999"),
        "Bihar": ("800000", "855999")
    }

    @classmethod
    def _load_dataset(cls):
        if cls._STATES is not None and cls._STATE_DISTRICTS_MAP is not None:
            return

        states_set: Set[str] = set()
        state_districts: Dict[str, Set[str]] = {}

        if os.path.exists(cls.DATASET_PATH):
            with open(cls.DATASET_PATH, mode="r", encoding="utf-8", errors="ignore") as f:
                reader = csv.DictReader(f)
                for row in reader:
                    state = (row.get("State") or "").strip()
                    district = (row.get("District") or "").strip()

                    if state:
                        states_set.add(state)
                        if state not in state_districts:
                            state_districts[state] = set()
                        if district:
                            state_districts[state].add(district)

        # Baseline fallback if dataset file is empty or unreachable
        if "Karnataka" not in states_set:
            states_set.add("Karnataka")
            state_districts["Karnataka"] = {
                "Kolar", "Bengaluru", "Bengaluru Rural", "Chikkaballapur",
                "Ramanagara", "Mysuru", "Tumakuru", "Mandya", "Hassan",
                "Chitradurga", "Davanagere", "Belagavi", "Bagalkot"
            }

        cls._STATES = sorted(list(states_set))
        cls._STATE_DISTRICTS_MAP = state_districts

    @classmethod
    def get_valid_states(cls) -> List[str]:
        cls._load_dataset()
        return list(cls._STATES or [])

    @classmethod
    def get_districts_for_state(cls, state: str) -> List[str]:
        cls._load_dataset()
        # Case-insensitive lookup
        target_state = state.strip()
        for s, dists in (cls._STATE_DISTRICTS_MAP or {}).items():
            if s.lower() == target_state.lower():
                return sorted(list(dists))
        return []

    @classmethod
    def validate_location(
        cls,
        state: str,
        district: str,
        pincode: str
    ) -> Tuple[bool, Optional[str]]:
        """
        Validates state, district, and pincode against the AGMARKNET dataset and Indian postal rules.
        """
        cls._load_dataset()

        # 1. Validate State
        valid_state_name: Optional[str] = None
        for s in (cls._STATES or []):
            if s.lower() == state.strip().lower():
                valid_state_name = s
                break

        if not valid_state_name:
            return False, f"State '{state}' is not found in official AGMARKNET records."

        # 2. Validate District in State
        districts = cls._STATE_DISTRICTS_MAP.get(valid_state_name, set())
        valid_district_name: Optional[str] = None
        for d in districts:
            if d.lower() == district.strip().lower():
                valid_district_name = d
                break

        if not valid_district_name:
            return False, f"District '{district}' does not exist in State '{valid_state_name}' in AGMARKNET records."

        # 3. Validate Pincode format (6 digits, cannot start with 0)
        pin_clean = pincode.strip()
        if not re.match(r"^[1-9][0-9]{5}$", pin_clean):
            return False, "Pincode must be exactly 6 numeric digits and cannot start with 0."

        # 4. Validate Pincode within State postal range if defined
        if valid_state_name in cls.STATE_PINCODE_RANGES:
            min_pin, max_pin = cls.STATE_PINCODE_RANGES[valid_state_name]
            if not (min_pin <= pin_clean <= max_pin):
                return False, f"Pincode {pin_clean} is outside the postal code range for {valid_state_name} ({min_pin[:2]}xxxx-{max_pin[:2]}xxxx)."

        return True, None
