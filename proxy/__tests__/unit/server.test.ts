import { describe, it, beforeEach, afterAll, vi, expect } from 'vitest';
import { startServer } from '../../src/server.js';

describe('startServer function', () => {
  const consoleMock = vi.spyOn(console, 'log').mockImplementation(() => undefined);
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterAll(() => {
    consoleMock.mockReset();
  });

  it('should start the server and listen on the specified port', async () => {
    const port = 3000;
    process.env.PORT = port.toString();

    await startServer({
        PORT: '3000',
        PEPPYRUS_TOKEN_TEST: 'test-peppyrus-token',
        ACCESS_TOKEN_KEY: 'test-access-token-key',
        DATABASE_URL: 'postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable',
    });

    expect(consoleMock).toHaveBeenCalledWith(`LetsPeppol listening on port ${port}`);
  });
});
