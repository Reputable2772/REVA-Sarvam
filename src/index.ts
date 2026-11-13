import express from 'express';
import cors from 'cors';
import { createMarketplaceRouter } from './routes/marketplaceRoutes.js';

export function createApp() {
  const app = express();
  app.use(cors());
  app.use(express.json());

  app.get('/health', (req, res) => {
    res.json({
      status: 'healthy',
      service: 'FarmRoute AI Multi-Buyer Marketplace Engine',
      version: '1.0.0',
      timestamp: new Date().toISOString()
    });
  });

  app.use('/api/marketplace', createMarketplaceRouter());

  return app;
}

if (process.env.NODE_ENV !== 'test' && !process.env.DISABLE_SERVER_LISTEN) {
  const app = createApp();
  const PORT = process.env.PORT || 4000;
  app.listen(PORT, () => {
    console.log(`🚀 FarmRoute AI Marketplace Service listening on port ${PORT}`);
  });
}
