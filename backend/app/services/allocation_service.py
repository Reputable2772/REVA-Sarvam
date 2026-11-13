import math
from typing import List, Dict, Any

class AllocationService:
    """
    Implements deterministic proportional allocation across pooled farmers
    using the Largest-Remainder (Hare-Niemeyer) method to guarantee zero
    quantity drift and exact sum conservation.
    """

    @staticmethod
    def allocate_target(
        lots: List[Dict[str, Any]],
        target_kg: float
    ) -> List[Dict[str, Any]]:
        """
        lots: List of dicts, each containing:
              - 'farmer_id'
              - 'lot_id'
              - 'quantity_kg'
        target_kg: Total buyer requirement volume (e.g. 800.0)
        """
        if not lots or target_kg <= 0:
            return []

        total_declared_kg = sum(l['quantity_kg'] for l in lots)
        if total_declared_kg <= 0:
            return []

        # If total declared is less than or equal to target, allocate full quantities
        if total_declared_kg <= target_kg:
            results = []
            for lot in lots:
                results.append({
                    'farmer_id': lot['farmer_id'],
                    'lot_id': lot['lot_id'],
                    'declared_kg': lot['quantity_kg'],
                    'allocated_kg': round(lot['quantity_kg'], 2),
                    'share_pct': round((lot['quantity_kg'] / total_declared_kg) * 100, 2)
                })
            return results

        # Proportional calculation with Largest-Remainder rounding
        # Scale to 2 decimal places (grams precision * 100)
        scale = 100
        target_scaled = int(round(target_kg * scale))
        
        allocations = []
        integer_sum = 0

        for lot in lots:
            exact_share = (lot['quantity_kg'] / total_declared_kg) * target_scaled
            floor_share = int(math.floor(exact_share))
            remainder = exact_share - floor_share
            integer_sum += floor_share

            allocations.append({
                'farmer_id': lot['farmer_id'],
                'lot_id': lot['lot_id'],
                'declared_kg': lot['quantity_kg'],
                'floor_share': floor_share,
                'remainder': remainder,
                'allocated_scaled': floor_share
            })

        # Remainder surplus to distribute
        surplus = target_scaled - integer_sum

        # Sort descending by remainder, with tie-breaking by lot_id for deterministic execution
        allocations.sort(key=lambda x: (x['remainder'], x['lot_id']), reverse=True)

        for i in range(surplus):
            allocations[i]['allocated_scaled'] += 1

        # Re-sort back to original lot order and convert back to kg
        lot_id_order = {lot['lot_id']: idx for idx, lot in enumerate(lots)}
        allocations.sort(key=lambda x: lot_id_order[x['lot_id']])

        final_results = []
        for a in allocations:
            allocated_kg = a['allocated_scaled'] / scale
            final_results.append({
                'farmer_id': a['farmer_id'],
                'lot_id': a['lot_id'],
                'declared_kg': a['declared_kg'],
                'allocated_kg': allocated_kg,
                'share_pct': round((allocated_kg / target_kg) * 100, 2)
            })

        # Verify exact sum conservation
        allocated_sum = sum(r['allocated_kg'] for r in final_results)
        assert abs(allocated_sum - target_kg) < 1e-4, f"Allocation drift detected: {allocated_sum} != {target_kg}"

        return final_results
