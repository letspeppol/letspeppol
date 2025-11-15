import jsonwebtoken from 'jsonwebtoken';

export async function checkBearerToken(
  token: string,
  secretKey: string,
): Promise<string> {
  return new Promise((resolve, reject) => {
    jsonwebtoken.verify(token, secretKey, (err, payload) => {
      if (err) {
        reject(err);
      } else {
        resolve(payload.peppolId);
      }
    });
  });
}

export async function checkServiceBearerToken(
  token: string,
  secretKey: string,
): Promise<string> {
  return new Promise((resolve, reject) => {
    jsonwebtoken.verify(token, secretKey, (err, payload) => {
      if (err) {
        reject(err);
      } else {
        resolve(payload.role);
      }
    });
  });
}
