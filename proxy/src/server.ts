import { createHmac } from 'crypto';
import express from 'express';
import cors from 'cors';
import { checkBearerToken, checkServiceBearerToken } from './auth.js';
import rateLimit from 'express-rate-limit';
import { Backend } from './Backend.js';
import { Scrada } from './scrada.js';
import {
  listEntityDocuments,
  getDocumentUbl,
  markDocumentAsPaid,
  getTotalsForUser,
  setDocumentStatus,
  getMaxDocumentStats,
  getTotalDocumentStats,
} from './db.js';

function getAuthMiddleware(secretKey: string): {
  checkAuth: express.RequestHandler;
  checkWebhook: express.RequestHandler;
  checkServiceAuth: express.RequestHandler;
} {
  return {
    checkAuth: async function checkAuth(req, res, next): Promise<void> {
      const authorization = req.headers['authorization'];
      if (!authorization) {
        res.status(401).json({ error: 'Unauthorized' });
      } else {
        const token = authorization.replace('Bearer ', '');
        try {
          const peppolId = await checkBearerToken(token, secretKey);
          req.peppolId = peppolId;
          next();
        } catch (err: { message: string } | unknown) {
          console.error('Error verifying token:', err);
          res.status(401).json({ error: (err as { message: string }).message });
        }
      }
    },
    checkWebhook: async function checkWebhook(req, res, next): Promise<void> {
      const key = process.env.SCRADA_COMPANY_KEY!;
      console.log(
        'Checking webhook with key',
        JSON.stringify(key),
        'and body',
        JSON.stringify(req.body),
      );
      const signatureComputed = createHmac('sha256', key)
        .update(req.body)
        .digest('hex');
      const signatureGiven = req.headers['x-scrada-hmac-sha256'];
      if (signatureComputed !== signatureGiven) {
        console.error('Invalid webhook signature:', {
          signatureComputed,
          signatureGiven,
        });
        res.status(401).json({ error: 'Unauthorized' });
      } else {
        req.body = JSON.parse(req.body);
        next();
      }
    },
    checkServiceAuth: async function checkServiceAuth(
      req,
      res,
      next,
    ): Promise<void> {
      const authorization = req.headers['authorization'];
      if (!authorization) {
        res.status(401).json({ error: 'Unauthorized' });
      } else {
        const token = authorization.replace('Bearer ', '');
        try {
          const role = await checkServiceBearerToken(token, secretKey);
          if (role !== 'service') {
            res.status(401).json({ error: 'Unauthorized' });
          } else {
            next();
          }
        } catch (err: { message: string } | unknown) {
          console.error('Error verifying token:', err);
          res.status(401).json({ error: (err as { message: string }).message });
        }
      }
    },
  };
}

export type ServerOptions = {
  PORT: string;
  ACCESS_TOKEN_KEY: string;
  DATABASE_URL: string;
};

const optionsToRequire = ['PORT', 'ACCESS_TOKEN_KEY', 'DATABASE_URL'];
export async function startServer(env: ServerOptions): Promise<number> {
  const { checkAuth, checkWebhook, checkServiceAuth } = getAuthMiddleware(
    env.ACCESS_TOKEN_KEY,
  );
  // console.error('checking', env);
  for (const option of optionsToRequire) {
    if (!env[option]) {
      throw new Error(`${option} is not set`);
    }
  }
  const backends = {
    scrada: new Scrada(),
  };
  function getBackend(peppolId: string): Backend {
    if (process.env.BACKEND) {
      console.log(
        'Using backend',
        process.env.BACKEND,
        ' because of BACKEND env var',
      );
      return backends[process.env.BACKEND];
    }
    const backendName = 'scrada';
    console.log('Using backend', backendName, 'for', peppolId);
    return backends[backendName];
  }

  async function hello(_req, res): Promise<void> {
    res.setHeader('Content-Type', 'text/plain');
    res.end("Let's Peppol!\n");
  }
  async function reg(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    console.log('Registering', sendingEntity);
    await backend.reg(sendingEntity, req.body.name || 'Business Entity Name');
    res.end('OK\n');
  }
  async function unreg(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    await backend.unreg(sendingEntity);
    res.end('OK\n');
  }
  async function send(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    await backend.sendDocument(req.body, sendingEntity);
    res.end('OK\n');
  }

  async function markPaid(req, res): Promise<void> {
    console.log(
      'Marking document as paid',
      req.peppolId,
      req.params.platformId,
      req.body,
    );
    await markDocumentAsPaid(
      req.peppolId,
      req.params.platformId,
      req.body.paid,
    );
    res.end('OK\n');
  }
  async function list(req, res): Promise<void> {
    const requestingEntity = req.peppolId;
    const query = {
      userId: requestingEntity as string,
      counterPartyId: req.query.counterPartyId as string | undefined,
      counterPartyNameLike: req.query.counterPartyNameLike as
        | string
        | undefined,
      docType: req.query.docType as 'invoice' | 'credit-note' | undefined,
      direction: req.query.direction as 'incoming' | 'outgoing' | undefined,
      docId: req.query.docId as string | undefined,
      sortBy:
        (req.query.sortBy as
          | 'amountAsc'
          | 'amountDesc'
          | 'createdAtAsc'
          | 'createdAtDesc'
          | undefined) || 'createdAtAsc',
      page: parseInt((req.query.page as string) || '1', 10),
      pageSize: parseInt((req.query.pageSize as string) || '20', 10),
    };
    const list = await listEntityDocuments(query);
    res.json(list);
  }
  async function getUbl(req, res): Promise<void> {
    const requestingEntity = req.peppolId;
    const ubl = await getDocumentUbl(requestingEntity, req.params.platformId);
    res.end(ubl);
  }
  async function getTotals(req, res): Promise<void> {
    const requestingEntity = req.peppolId;
    const totals = await getTotalsForUser(requestingEntity);
    res.json(totals);
  }
  async function getTotalDocuments(_req, res): Promise<void> {
    const totals = await getTotalDocumentStats();
    res.json(totals);
  }
  async function getMaxDocuments(_req, res): Promise<void> {
    const totals = await getMaxDocumentStats();
    res.json(totals);
  }
  const port = parseInt(env.PORT);
  const app = express();
  app.use(cors({ origin: true })); // Reflect (enable) the requested origin in the CORS response
  // Apply rate limiting to all requests
  app.use(
    rateLimit({
      windowMs: 15 * 60 * 1000, // 15 minutes
      max: 100, // Limit each IP to 100 requests per `window` (here, per 15 minutes)
      message: {
        status: 429,
        error: 'Too many requests, please try again later.',
      },
      headers: true, // Include rate limit info in response headers
    }),
  );
  return new Promise((resolve, reject) => {
    app.get('/v2/', hello);
    app.get('/v2/totals', checkAuth, getTotals);
    app.get('/v2/stats/totals', checkServiceAuth, getTotalDocuments);
    app.get('/v2/stats/max', checkServiceAuth, getMaxDocuments);
    app.get('/v2/documents', checkAuth, list);
    app.get('/v2/documents/:platformId', checkAuth, getUbl);
    app.post('/v2/documents/:platformId', checkAuth, express.json(), markPaid);
    app.post('/v2/send', checkAuth, express.text({ type: '*/*' }), send);
    app.post('/v2/reg', checkAuth, express.json(), reg);
    app.post('/v2/unreg', checkAuth, express.json(), unreg);
    app.post(
      '/v2/webhook/outgoing/scrada',
      express.text({ type: '*/*' }),
      checkWebhook,
      async (req, res) => {
        console.log('Received outgoing webhook from Scrada', req.body);
        setDocumentStatus(req.body);
        res.status(200).end('OK\n');
      },
    );
    // app.post('/v2/webhook/incoming/scrada', express.text({ type: '*/*' }), checkWebhook, async (req, res) => {
    //   console.log('Received incoming webhook from Scrada', req.body);
    //   res.status(200).end('OK\n');
    // });

    app.listen(port, (error) => {
      if (error) {
        reject(error);
      } else {
        console.log(`LetsPeppol listening on port ${port}`);
        resolve(0);
      }
    });
  });
}
