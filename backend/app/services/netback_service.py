from typing import Dict, Any

class NetbackService:
    """
    Deterministic Netback Arbitrage Calculator.
    Compares 3 distinct selling channels for smallholders:
    1. Local Mandi (Kolar APMC)
    2. Distant Mandi (Bangarpet APMC - Headline Price Trap)
    3. Direct Institutional Buyer via Virtual FPO
    """

    @staticmethod
    def calculate_comparison(
        quantity_kg: float,
        crop: str = "Tomato",
        kolar_rate: float = 24.0,
        bangarpet_rate: float = 28.0,
        direct_rate: float = 28.50,
        shared_freight_cost: float = 549.0
    ) -> Dict[str, Any]:
        """
        Computes the complete cost deductions and effective net return per kg
        across all three selling routes.
        """
        # 1. Local Mandi (Kolar APMC)
        kolar_gross = quantity_kg * kolar_rate
        kolar_transport = 350.0 # Dedicated local auto/small pickup
        kolar_loading = 80.0
        kolar_commission = round(kolar_gross * 0.065, 2) # 6.5% APMC agent commission
        kolar_weighing = 30.0
        kolar_rejection_loss = round(quantity_kg * 0.05 * kolar_rate, 2) # 5% transit & sorting loss
        kolar_total_deductions = kolar_transport + kolar_loading + kolar_commission + kolar_weighing + kolar_rejection_loss
        kolar_net = round(kolar_gross - kolar_total_deductions, 2)
        kolar_effective_per_kg = round(kolar_net / quantity_kg, 2)

        # 2. Distant Mandi (Bangarpet APMC) - HEADLINE PRICE TRAP
        bangarpet_gross = quantity_kg * bangarpet_rate
        bangarpet_transport = 750.0 # Longer 39km rural haul
        bangarpet_loading = 100.0
        bangarpet_commission = round(bangarpet_gross * 0.05, 2) # 5.0% commission
        bangarpet_weighing = 30.0
        bangarpet_rejection_loss = round(quantity_kg * 0.10 * bangarpet_rate, 2) # 10% damage/spoilage on longer transit
        bangarpet_total_deductions = bangarpet_transport + bangarpet_loading + bangarpet_commission + bangarpet_weighing + bangarpet_rejection_loss
        bangarpet_net = round(bangarpet_gross - bangarpet_total_deductions, 2)
        bangarpet_effective_per_kg = round(bangarpet_net / quantity_kg, 2)

        # 3. Direct Institutional Buyer via Virtual FPO
        direct_gross = quantity_kg * direct_rate
        direct_transport = shared_freight_cost # Prorated shared freight (Tata Ace route)
        direct_loading = 50.0
        direct_commission = 0.0 # Zero middleman commission
        direct_platform_fee = 30.0
        direct_rejection_loss = 0.0 # Zero distress loss due to pre-graded acceptance
        direct_total_deductions = direct_transport + direct_loading + direct_commission + direct_platform_fee + direct_rejection_loss
        direct_net = round(direct_gross - direct_total_deductions, 2)
        direct_effective_per_kg = round(direct_net / quantity_kg, 2)

        # Net gains
        gain_over_kolar = round(direct_net - kolar_net, 2)
        gain_pct_kolar = round((gain_over_kolar / kolar_net) * 100, 1)

        gain_over_bangarpet = round(direct_net - bangarpet_net, 2)
        gain_pct_bangarpet = round((gain_over_bangarpet / bangarpet_net) * 100, 1)

        # Headline trap detected if Bangarpet headline price is higher but net earnings are lower
        headline_trap_detected = (bangarpet_rate > kolar_rate) and (bangarpet_net < kolar_net)

        return {
            "quantity_kg": quantity_kg,
            "crop": crop,
            "channels": [
                {
                    "id": "local_mandi",
                    "name": "Local Mandi (Kolar APMC)",
                    "headline_rate_per_kg": kolar_rate,
                    "gross_revenue": kolar_gross,
                    "deductions": {
                        "transport": kolar_transport,
                        "loading_unloading": kolar_loading,
                        "commission": kolar_commission,
                        "weighing_fee": kolar_weighing,
                        "rejection_loss": kolar_rejection_loss
                    },
                    "total_costs": kolar_total_deductions,
                    "net_earnings": kolar_net,
                    "effective_rate_per_kg": kolar_effective_per_kg
                },
                {
                    "id": "distant_mandi",
                    "name": "Distant Mandi (Bangarpet APMC)",
                    "headline_rate_per_kg": bangarpet_rate,
                    "gross_revenue": bangarpet_gross,
                    "deductions": {
                        "transport": bangarpet_transport,
                        "loading_unloading": bangarpet_loading,
                        "commission": bangarpet_commission,
                        "weighing_fee": bangarpet_weighing,
                        "rejection_loss": bangarpet_rejection_loss
                    },
                    "total_costs": bangarpet_total_deductions,
                    "net_earnings": bangarpet_net,
                    "effective_rate_per_kg": bangarpet_effective_per_kg,
                    "is_headline_trap": headline_trap_detected,
                    "trap_warning": "⚠️ Bangarpet offers +₹4 headline price but yields LESS net cash due to ₹750 transport and 10% transit loss!" if headline_trap_detected else None
                },
                {
                    "id": "virtual_fpo_direct",
                    "name": "Direct Buyer (Reliance Fresh via Virtual FPO)",
                    "headline_rate_per_kg": direct_rate,
                    "gross_revenue": direct_gross,
                    "deductions": {
                        "shared_freight": direct_transport,
                        "handling": direct_loading,
                        "commission": direct_commission,
                        "platform_fee": direct_platform_fee
                    },
                    "total_costs": direct_total_deductions,
                    "net_earnings": direct_net,
                    "effective_rate_per_kg": direct_effective_per_kg,
                    "benefit_over_local_mandi": f"+₹{gain_over_kolar} (+{gain_pct_kolar}%)",
                    "benefit_over_distant_mandi": f"+₹{gain_over_bangarpet} (+{gain_pct_bangarpet}%)"
                }
            ],
            "recommendation": "Direct Buyer via Virtual FPO",
            "net_advantage_amount": gain_over_kolar,
            "net_advantage_pct": gain_pct_kolar,
            "headline_trap_detected": headline_trap_detected
        }
