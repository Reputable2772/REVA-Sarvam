import { Router, Request, Response } from 'express';
import { NetbackEngine } from '../services/netbackEngine.js';

export function createNetbackRouter(): Router {
  const router = Router();
  const engine = new NetbackEngine();

  // POST /api/netback/compare
  router.post('/compare', (req: Request, res: Response) => {
    const { lot_id, crop, quantity_kg, location, mandiRatePerKg, directBuyerRatePerKg } = req.body;

    if (!lot_id || !crop || !quantity_kg) {
      return res.status(400).json({
        success: false,
        error: 'Missing required fields: lot_id, crop, quantity_kg.'
      });
    }

    const result = engine.calculateNetback({
      lot_id: String(lot_id),
      crop: String(crop),
      quantity_kg: Number(quantity_kg),
      location: String(location || 'Kolar'),
      mandiRatePerKg: mandiRatePerKg ? Number(mandiRatePerKg) : undefined,
      directBuyerRatePerKg: directBuyerRatePerKg ? Number(directBuyerRatePerKg) : undefined
    });

    res.json(result);
  });

  return router;
}
