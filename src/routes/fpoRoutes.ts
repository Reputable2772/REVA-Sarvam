import { Router, Request, Response } from 'express';
import { VirtualFpoEngine } from '../services/virtualFpoEngine.js';

export function createFpoRouter(): Router {
  const router = Router();
  const engine = new VirtualFpoEngine();

  // POST /api/fpo/pool
  router.post('/pool', (req: Request, res: Response) => {
    const { crop, grade, buyer_order_id, target_quantity_kg, price_per_kg, total_transport_cost, participating_lots } = req.body;

    if (!crop || !target_quantity_kg || !Array.isArray(participating_lots)) {
      return res.status(400).json({
        success: false,
        error: 'Missing crop, target_quantity_kg, or participating_lots array.'
      });
    }

    const poolResult = engine.poolLots({
      crop: String(crop),
      grade: grade ? String(grade) : undefined,
      buyer_order_id: buyer_order_id ? String(buyer_order_id) : undefined,
      target_quantity_kg: Number(target_quantity_kg),
      price_per_kg: price_per_kg ? Number(price_per_kg) : undefined,
      total_transport_cost: total_transport_cost ? Number(total_transport_cost) : undefined,
      participating_lots: participating_lots.map(String)
    });

    res.json(poolResult);
  });

  return router;
}
