import { Router, Request, Response } from 'express';
import { LogisticsEngine, VILLAGE_DIRECTORY } from '../services/logisticsEngine.js';
import { SEED_HARVEST_LOTS } from '../data/seedData.js';
import { HarvestLot } from '../types/index.js';

export function createLogisticsRouter(): Router {
  const router = Router();

  // 1. GET /api/logistics/vehicles - Fleet specification list
  router.get('/vehicles', (_req: Request, res: Response) => {
    res.json({
      success: true,
      fleet: LogisticsEngine.getFleet()
    });
  });

  // 2. GET /api/logistics/villages - Supported regional corridor directory
  router.get('/villages', (_req: Request, res: Response) => {
    res.json({
      success: true,
      villages: Object.values(VILLAGE_DIRECTORY)
    });
  });

  // 3. POST /api/logistics/quote - Quick vehicle recommendation & freight quote
  router.post('/quote', (req: Request, res: Response) => {
    const { weightKg, distanceKm } = req.body;
    if (!weightKg || Number(weightKg) <= 0) {
      return res.status(400).json({
        success: false,
        error: 'Valid weightKg (> 0) is required.'
      });
    }

    try {
      const vehicle = LogisticsEngine.recommendVehicle(Number(weightKg));
      const dist = distanceKm ? Number(distanceKm) : 80;
      const totalFreight = vehicle.baseCharge + dist * vehicle.perKmRate + vehicle.loadingUnloadingCost;

      res.json({
        success: true,
        weightKg: Number(weightKg),
        recommendedVehicle: vehicle,
        estimatedDistanceKm: dist,
        estimatedTotalFreightCost: totalFreight,
        freightPerKg: Math.round((totalFreight / Number(weightKg)) * 100) / 100
      });
    } catch (err: any) {
      res.status(400).json({ success: false, error: err.message });
    }
  });

  // 4. POST /api/logistics/plan - Generate multi-village consolidation and prorated cost plan
  router.post('/plan', (req: Request, res: Response) => {
    const { lotIds, customLots, destination } = req.body;

    let lotsToPlan: HarvestLot[] = [];

    if (Array.isArray(customLots) && customLots.length > 0) {
      lotsToPlan = customLots;
    } else if (Array.isArray(lotIds) && lotIds.length > 0) {
      lotsToPlan = SEED_HARVEST_LOTS.filter((l) => lotIds.includes(l.lot_id));
    } else {
      // Default to the full 6-farmer Kolar tomato baseline
      lotsToPlan = SEED_HARVEST_LOTS;
    }

    if (lotsToPlan.length === 0) {
      return res.status(400).json({
        success: false,
        error: 'No valid harvest lots found to compute logistics plan.'
      });
    }

    try {
      const plan = LogisticsEngine.allocateSharedFreight(
        lotsToPlan,
        destination || 'Bengaluru DC, Whitefield'
      );
      res.json({
        success: true,
        plan
      });
    } catch (err: any) {
      res.status(400).json({ success: false, error: err.message });
    }
  });

  return router;
}
