import express from 'express';
import cors from 'cors';
import { createMarketplaceRouter } from './routes/marketplaceRoutes.js';
import { createNetbackRouter } from './routes/netbackRoutes.js';
import { createFpoRouter } from './routes/fpoRoutes.js';

export function createApp() {
  const app = express();
  app.use(cors());
  app.use(express.json());

  app.get('/health', (req, res) => {
    res.json({
      status: 'healthy',
      service: 'FarmRoute AI Unified Engine',
      version: '1.0.0',
      modules: ['marketplace', 'netback', 'virtual-fpo'],
      timestamp: new Date().toISOString()
    });
  });

  app.use('/api/marketplace', createMarketplaceRouter());
  app.use('/api/netback', createNetbackRouter());
  app.use('/api/fpo', createFpoRouter());

  return app;
}

if (process.env.NODE_ENV !== 'test' && !process.env.DISABLE_SERVER_LISTEN) {
  const app = createApp();
  const PORT = process.env.PORT || 4000;
  app.listen(PORT, () => {
    console.log(`🚀 FarmRoute AI Unified Service listening on port ${PORT}`);
  });
}
