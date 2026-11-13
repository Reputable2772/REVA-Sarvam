import hashlib
import time
from datetime import datetime, timezone
from .models import UpiPayoutRequest, UpiPayoutResponse

class EscrowService:
    def __init__(self):
        self.payouts: dict[str, UpiPayoutResponse] = {}
        self.payout_counter = 1

    def execute_instant_upi_payout(self, req: UpiPayoutRequest) -> UpiPayoutResponse:
        total_deductions = round(req.transport_deduction + req.platform_fee, 2)
        net_amount = round(req.gross_amount - total_deductions, 2)
        
        if net_amount < 0:
            raise ValueError(f"Deductions (₹{total_deductions}) cannot exceed gross amount (₹{req.gross_amount})")

        payout_id = f"PAY-UPI-{self.payout_counter:05d}"
        self.payout_counter += 1

        # Deterministic simulated NPCI / Bank RRN reference
        raw_hash = hashlib.sha256(f"{payout_id}:{req.farmer_id}:{time.time()}".encode()).hexdigest()
        rrn_ref = f"NPCI{raw_hash[:12].upper()}"

        now = datetime.now(timezone.utc).isoformat()

        response = UpiPayoutResponse(
            payout_id=payout_id,
            lot_id=req.lot_id,
            farmer_id=req.farmer_id,
            farmer_name=req.farmer_name,
            upi_vpa=req.upi_vpa,
            gross_amount=req.gross_amount,
            total_deductions=total_deductions,
            net_payout=net_amount,
            rrn_reference=rrn_ref,
            status="SUCCESS",
            timestamp=now
        )

        self.payouts[payout_id] = response
        return response

    def get_payout(self, payout_id: str):
        return self.payouts.get(payout_id)
