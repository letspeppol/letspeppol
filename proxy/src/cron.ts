export async function checkForIncomingDocs(): Promise<void> {
  console.log('Checking for incoming documents...');
  const res = await fetch(
      `https://apitest.scrada.be/v1/company/${process.env.SCRADA_COMPANY_ID}/peppol/inbound/document/unconfirmed`,
    {
        headers: {
          'X-Api-Key': process.env.SCRADA_API_KEY!,
          'X-Password': process.env.SCRADA_API_PWD!,
        },
      },
    );
  if (!res.ok) {
    console.error('Failed to fetch incoming documents:', res.statusText);
    return;
  }
  const documents = await res.json();
  console.log('Incoming documents:', documents);
}
