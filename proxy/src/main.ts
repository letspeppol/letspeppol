import 'dotenv/config';
import { startServer, ServerOptions } from './server.js';
import { checkForIncomingDocs } from './cron.js';

// ...
startServer(process.env as unknown as ServerOptions);
setInterval(checkForIncomingDocs, 5 * 60 * 1000); // Check every 5 minutes
checkForIncomingDocs(); // Initial check on startup
