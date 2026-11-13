import { HarvestLot } from '../types/index.js';
import { SEED_HARVEST_LOTS } from '../data/seedData.js';

export interface FarmerPayoutShare {
  farmer_id: string;
  farmer_name: string;
  lot_id: string;
  village: string;
  contribution_kg: number;
  share_pct: number;
  gross_amount: number;
  allocated_transport_cost: number;
  net_payout: number;
}

export interface FpoPoolResult {
  fpo_pool_id: string;
  crop: string;
  grade: string;
  total_pooled_kg: number;
  buyer_target_kg: number;
  status: 'fulfilled' | 'partial' | 'pending';
  price_per_kg: number;
  total_order_value: number;
  shared_transport_total: number;
  farmer_shares: FarmerPayoutShare[];
  logistics_savings_total: number;
}

export class VirtualFpoEngine {
  private lotsMap: Map<string, HarvestLot> = new Map();

  constructor(initialLots: HarvestLot[] = SEED_HARVEST_LOTS) {
    initialLots.forEach((lot) => this.lotsMap.set(lot.lot_id, lot));
  }

  public registerLot(lot: HarvestLot) {
    this.lotsMap.set(lot.lot_id, lot);
  }

  public poolLots(params: {
    crop: string;
    grade?: string;
    buyer_order_id?: string;
    target_quantity_kg: number;
    price_per_kg?: number;
    total_transport_cost?: number;
    participating_lots: string[];
  }): FpoPoolResult {
    const pricePerKg = params.price_per_kg || 28.0;
    const totalTransport = params.total_transport_cost || 900.0;
    const grade = params.grade || 'Grade A';

    const lots: HarvestLot[] = [];
    for (const id of params.participating_lots) {
      const found = this.lotsMap.get(id);
      if (found) {
        lots.push(found);
      }
    }

    const totalPooledKg = lots.reduce((sum, l) => sum + l.quantity_kg, 0);
    const totalOrderValue = totalPooledKg * pricePerKg;

    // Standard individual mandi transport is ~350 per farmer
    const individualTransportTotal = lots.length * 350.0;
    const logisticsSavings = Math.max(0, individualTransportTotal - totalTransport);

    const farmerShares: FarmerPayoutShare[] = lots.map((l) => {
      const sharePct = totalPooledKg > 0 ? (l.quantity_kg / totalPooledKg) * 100 : 0;
      const gross = l.quantity_kg * pricePerKg;
      // Pro-rata transport cost allocation
      const transportShare = Math.round((totalTransport * (sharePct / 100)) * 100) / 100;
      const netPayout = Math.round((gross - transportShare) * 100) / 100;

      return {
        farmer_id: l.farmer_id,
        farmer_name: l.farmer_name,
        lot_id: l.lot_id,
        village: l.village,
        contribution_kg: l.quantity_kg,
        share_pct: Math.round(sharePct * 100) / 100,
        gross_amount: gross,
        allocated_transport_cost: transportShare,
        net_payout: netPayout
      };
    });

    const status = totalPooledKg >= params.target_quantity_kg ? 'fulfilled' : 'partial';

    return {
      fpo_pool_id: `VFPO-KLR-${params.crop.toUpperCase()}-01`,
      crop: params.crop,
      grade,
      total_pooled_kg: totalPooledKg,
      buyer_target_kg: params.target_quantity_kg,
      status,
      price_per_kg: pricePerKg,
      total_order_value: totalOrderValue,
      shared_transport_total: totalTransport,
      farmer_shares: farmerShares,
      logistics_savings_total: logisticsSavings
    };
  }
}
