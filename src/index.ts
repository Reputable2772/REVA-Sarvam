import express from 'express';
import cors from 'cors';
import { createMarketplaceRouter } from './routes/marketplaceRoutes.js';
import { createNetbackRouter } from './routes/netbackRoutes.js';
import { createFpoRouter } from './routes/fpoRoutes.js';
import { createLogisticsRouter } from './routes/logisticsRoutes.js';

export function createApp() {
  const app = express();
  app.use(cors());
  app.use(express.json());

  app.get('/health', (_req, res) => {
    res.json({
      status: 'healthy',
      service: 'FarmRoute AI Unified Engine',
      version: '1.1.0',
      modules: ['marketplace', 'netback', 'virtual-fpo', 'logistics'],
      timestamp: new Date().toISOString()
    });
  });

  app.use('/api/marketplace', createMarketplaceRouter());
  app.use('/api/netback', createNetbackRouter());
  app.use('/api/fpo', createFpoRouter());
  app.use('/api/logistics', createLogisticsRouter());

  return app;
}

if (process.env.NODE_ENV !== 'test' && !process.env.DISABLE_SERVER_LISTEN) {
  const app = createApp();
  const PORT = process.env.PORT || 4000;
  app.listen(PORT, () => {
    console.log(`🚀 FarmRoute AI Unified Service listening on port ${PORT}`);
  });
}
