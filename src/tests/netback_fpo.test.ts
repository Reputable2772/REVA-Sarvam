import { describe, it } from 'node:test';
import assert from 'node:assert';
import { NetbackEngine } from '../services/netbackEngine.js';
import { VirtualFpoEngine } from '../services/virtualFpoEngine.js';

describe('FarmRoute AI - Netback & Virtual FPO Engines', () => {
  const netbackEngine = new NetbackEngine();
  const fpoEngine = new VirtualFpoEngine();

  it('should compute netback comparisons accurately', () => {
    const res = netbackEngine.calculateNetback({
      lot_id: 'FR-KLR-00251',
      crop: 'Tomato',
      quantity_kg: 100,
      location: 'Mulbagal',
      mandiRatePerKg: 22.0,
      directBuyerRatePerKg: 28.0
    });

    assert.strictEqual(res.lot_id, 'FR-KLR-00251');
    assert.strictEqual(res.options.length, 2);

    const mandi = res.options[0];
    const buyer = res.options[1];

    assert.strictEqual(mandi.gross_revenue, 2200);
    assert.strictEqual(mandi.total_costs, 603);
    assert.strictEqual(mandi.net_return, 1597);

    assert.strictEqual(buyer.gross_revenue, 2800);
    assert.strictEqual(buyer.total_costs, 250);
    assert.strictEqual(buyer.net_return, 2550);

    assert.ok(buyer.net_return > mandi.net_return);
    assert.strictEqual(res.recommendation, 'Direct Buyer via Virtual FPO');
  });

  it('should pool 6 farmer lots into 600kg buyer target and compute individual payouts', () => {
    const participatingLots = [
      'FR-KLR-00251',
      'FR-KLR-00252',
      'FR-KLR-00253',
      'FR-KLR-00254',
      'FR-KLR-00255',
      'FR-KLR-00256'
    ];

    const pool = fpoEngine.poolLots({
      crop: 'Tomato',
      grade: 'Grade A',
      target_quantity_kg: 600,
      price_per_kg: 28.0,
      total_transport_cost: 900.0,
      participating_lots: participatingLots
    });

    assert.strictEqual(pool.total_pooled_kg, 600);
    assert.strictEqual(pool.buyer_target_kg, 600);
    assert.strictEqual(pool.status, 'fulfilled');
    assert.strictEqual(pool.total_order_value, 16800);
    assert.strictEqual(pool.farmer_shares.length, 6);

    // Each farmer should contribute 100kg and receive allocated freight of 150
    for (const share of pool.farmer_shares) {
      assert.strictEqual(share.contribution_kg, 100);
      assert.strictEqual(share.allocated_transport_cost, 150.0);
      assert.strictEqual(share.gross_amount, 2800.0);
      assert.strictEqual(share.net_payout, 2650.0);
    }

    // Shared transport savings over individual mandi trips (6 * 350 = 2100 - 900 = 1200 savings)
    assert.strictEqual(pool.logistics_savings_total, 1200);
  });
});
