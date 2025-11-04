import express from 'express';
import cors from 'cors';
import { checkBearerToken } from './auth.js';
import rateLimit from 'express-rate-limit';
import { Backend } from './Backend.js';
import { Scrada } from './scrada.js';
import { listEntityDocuments } from './db.js';

function getAuthMiddleware(secretKey: string): express.RequestHandler {
  return async function checkAuth(req, res, next): Promise<void> {
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
  };
}

export type ServerOptions = {
  PORT: string;
  PEPPYRUS_TOKEN_TEST: string;
  ACCESS_TOKEN_KEY: string;
  DATABASE_URL: string;
};

const optionsToRequire = [
  'PORT',
  'PEPPYRUS_TOKEN_TEST',
  'ACCESS_TOKEN_KEY',
  'DATABASE_URL',
];
export async function startServer(env: ServerOptions): Promise<number> {
  const checkAuth = getAuthMiddleware(env.ACCESS_TOKEN_KEY);
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
  async function listV1(req, res): Promise<void> {
    const documents = await listEntityDocuments({
      peppolId: req.peppolId,
      direction: req.params.direction,
      type: req.params.docType,
      query: req.query,
      apiVersion: 'v1',
      page: req.query.page ? parseInt(req.query.page as string) : 1,
      pageSize: req.query.pageSize
        ? parseInt(req.query.pageSize as string)
        : 20,
    });
    res.setHeader('Content-Type', 'application/json');
    res.json(documents);
  }
  async function get(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const xml = await backend.getDocumentXml({
      peppolId: req.peppolId,
      type: req.params.docType,
      uuid: req.params.uuid,
      direction: req.params.direction,
    });
    res.setHeader('Content-Type', 'text/xml');
    res.send(xml);
  }
  async function send(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    await backend.sendDocument(req.body, sendingEntity);
    res.end('OK\n');
  }
  async function reg(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    console.log('Registering', sendingEntity);
    await backend.reg(sendingEntity);
    res.end('OK\n');
  }
  async function unreg(req, res): Promise<void> {
    const backend = getBackend(req.peppolId);
    const sendingEntity = req.peppolId;
    await backend.unreg(sendingEntity);
    res.end('OK\n');
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
  app.use(express.json());
  return new Promise((resolve, reject) => {
    app.get('/v2/', hello);
    app.get('/v2/:docType/:direction', checkAuth, listV1);
    app.get('/v2/:docType/:direction/:uuid', checkAuth, get);
    app.post('/v2/send', checkAuth, express.text({ type: '*/*' }), send);
    app.post('/v2/reg', checkAuth, reg);
    app.post('/v2/unreg', checkAuth, unreg);

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
