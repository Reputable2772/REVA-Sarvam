export interface NetbackOption {
  channel: string;
  market_price_per_kg: number;
  gross_revenue: number;
  deductions: {
    transport_cost: number;
    loading_unloading: number;
    mandi_commission_pct?: number;
    commission_amount?: number;
    weighing_fee?: number;
    platform_fee?: number;
  };
  total_costs: number;
  net_return: number;
  effective_rate_per_kg: number;
  benefit_over_mandi?: string;
}

export interface NetbackComparisonResult {
  lot_id: string;
  crop: string;
  quantity_kg: number;
  location: string;
  options: NetbackOption[];
  recommendation: string;
  farmer_savings: number;
}

export class NetbackEngine {
  public calculateNetback(params: {
    lot_id: string;
    crop: string;
    quantity_kg: number;
    location: string;
    mandiRatePerKg?: number;
    directBuyerRatePerKg?: number;
  }): NetbackComparisonResult {
    const qty = params.quantity_kg;
    const mandiRate = params.mandiRatePerKg || 22.0;
    const buyerRate = params.directBuyerRatePerKg || 28.0;

    // Mandi Channel calculation
    const mandiGross = qty * mandiRate;
    const mandiTransport = 350.0;
    const mandiLoading = 80.0;
    const mandiCommissionPct = 6.5;
    const mandiCommissionAmount = Math.round((mandiGross * (mandiCommissionPct / 100)) * 100) / 100;
    const mandiWeighing = 30.0;
    const mandiTotalCosts = mandiTransport + mandiLoading + mandiCommissionAmount + mandiWeighing;
    const mandiNetReturn = Math.round((mandiGross - mandiTotalCosts) * 100) / 100;
    const mandiEffectiveRate = Math.round((mandiNetReturn / qty) * 100) / 100;

    const mandiOption: NetbackOption = {
      channel: 'Local Mandi (Kolar APMC)',
      market_price_per_kg: mandiRate,
      gross_revenue: mandiGross,
      deductions: {
        transport_cost: mandiTransport,
        loading_unloading: mandiLoading,
        mandi_commission_pct: mandiCommissionPct,
        commission_amount: mandiCommissionAmount,
        weighing_fee: mandiWeighing
      },
      total_costs: mandiTotalCosts,
      net_return: mandiNetReturn,
      effective_rate_per_kg: mandiEffectiveRate
    };

    // Direct Buyer (Virtual FPO) calculation
    const buyerGross = qty * buyerRate;
    const buyerTransport = 150.0; // Pooled shared freight
    const buyerLoading = 50.0;
    const buyerPlatformFee = 50.0;
    const buyerTotalCosts = buyerTransport + buyerLoading + buyerPlatformFee;
    const buyerNetReturn = Math.round((buyerGross - buyerTotalCosts) * 100) / 100;
    const buyerEffectiveRate = Math.round((buyerNetReturn / qty) * 100) / 100;

    const netGain = Math.round((buyerNetReturn - mandiNetReturn) * 100) / 100;
    const pctGain = Math.round(((buyerNetReturn - mandiNetReturn) / mandiNetReturn) * 1000) / 10;

    const buyerOption: NetbackOption = {
      channel: 'Direct Buyer (Reliance Fresh / Supermarket via Virtual FPO)',
      market_price_per_kg: buyerRate,
      gross_revenue: buyerGross,
      deductions: {
        transport_cost: buyerTransport,
        loading_unloading: buyerLoading,
        platform_fee: buyerPlatformFee
      },
      total_costs: buyerTotalCosts,
      net_return: buyerNetReturn,
      effective_rate_per_kg: buyerEffectiveRate,
      benefit_over_mandi: `+₹${netGain} (+${pctGain}%)`
    };

    return {
      lot_id: params.lot_id,
      crop: params.crop,
      quantity_kg: qty,
      location: params.location,
      options: [mandiOption, buyerOption],
      recommendation: 'Direct Buyer via Virtual FPO',
      farmer_savings: netGain
    };
  }
}
