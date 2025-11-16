// import {jsPDF} from "jspdf";
// import {getAmount, Invoice, UBLDoc, UBLLine} from "../../services/peppol/ubl";
//
// // Basic PDF generator for an Invoice / Credit Note.
// // Layout:
// //  - Supplier (AccountingSupplierParty) top-left
// //  - Customer (AccountingCustomerParty) top-right
// //  - Title + meta (Invoice number, dates)
// //  - Line items table (simple)
// //  - Totals at bottom
// // This is intentionally minimal; can be extended with styling, pagination, tax breakdown, etc.
//
// interface ColumnDef<T> {
//   header: string;
//   width: number; // in mm
//   accessor: (row: T) => string;
//   align?: "left" | "right";
// }
//
// const MARGIN_X = 15;
// const MARGIN_Y = 15;
// const LINE_HEIGHT = 6;
//
// function formatMoney(value?: number): string {
//   if (value === undefined || value === null || Number.isNaN(value)) return "-";
//   return value.toFixed(2);
// }
//
// function getDocType(doc: UBLDoc) {
//   return ("InvoiceTypeCode" in doc) ? "INVOICE" : "CREDIT NOTE";
// }
//
// function drawBlock(docPdf: jsPDF, title: string, lines: string[], x: number, y: number, maxWidth: number) {
//   docPdf.setFontSize(10).setFont(undefined, "bold");
//   docPdf.text(title, x, y);
//   docPdf.setFont(undefined, "normal");
//   let cursorY = y + LINE_HEIGHT * 0.8;
//   lines.forEach(l => {
//     if (!l) return;
//     const splitted = docPdf.splitTextToSize(l, maxWidth);
//     splitted.forEach(strLine => {
//       if (cursorY > docPdf.internal.pageSize.getHeight() - MARGIN_Y) {
//         docPdf.addPage();
//         cursorY = MARGIN_Y;
//       }
//       docPdf.text(strLine, x, cursorY);
//       cursorY += LINE_HEIGHT * 0.75;
//     });
//   });
//   return cursorY;
// }
//
// function buildPartyLines(partyWrapper: Invoice["AccountingSupplierParty"], labelPrefix?: string): string[] {
//   if (!partyWrapper?.Party) return [];
//   const p = partyWrapper.Party;
//   const addr = p.PostalAddress;
//   const lines: string[] = [];
//   if (p.PartyName?.Name) lines.push(p.PartyName.Name);
//   if (addr) {
//     const addrParts = [addr.StreetName, addr.AdditionalStreetName].filter(Boolean).join(" ");
//     if (addrParts) lines.push(addrParts);
//     const cityLine = [addr.PostalZone, addr.CityName].filter(Boolean).join(" ");
//     if (cityLine) lines.push(cityLine);
//     if (addr.Country?.IdentificationCode) lines.push(addr.Country.IdentificationCode);
//   }
//   if (p.PartyTaxScheme?.CompanyID) lines.push(`VAT: ${p.PartyTaxScheme.CompanyID}`);
//   if (labelPrefix) {
//     // Could prepend label, but we embed title separately.
//   }
//   return lines;
// }
//
// export function generateInvoicePdf(doc: UBLDoc): jsPDF {
//   const pdf = new jsPDF({ unit: "mm", format: "a4" });
//   const pageWidth = pdf.internal.pageSize.getWidth();
//   const contentWidth = pageWidth - MARGIN_X * 2;
//
//   // Header
//   const typeLabel = getDocType(doc);
//   pdf.setFontSize(18).setFont(undefined, "bold");
//   pdf.text(typeLabel, MARGIN_X, MARGIN_Y);
//   pdf.setFontSize(11).setFont(undefined, "normal");
//
//   const rightX = pageWidth - MARGIN_X - 60; // allocate ~60mm for right block
//   const supplierLines = buildPartyLines(doc.AccountingSupplierParty);
//   const customerLines = buildPartyLines(doc.AccountingCustomerParty);
//
//   // Supplier (left) & Customer (right)
//   drawBlock(pdf, "Supplier", supplierLines, MARGIN_X, MARGIN_Y + 8, 60);
//   drawBlock(pdf, "Customer", customerLines, rightX, MARGIN_Y + 8, 60);
//
//   // Meta block beneath header area
//   let metaY = MARGIN_Y + 8 + Math.max(supplierLines.length, customerLines.length) * (LINE_HEIGHT * 0.75) + 6;
//   if (metaY < 50) metaY = 50; // ensure some spacing
//
//   const meta: string[] = [];
//   meta.push(`Number: ${doc.ID}`);
//   meta.push(`Issue Date: ${doc.IssueDate}`);
//   if (doc.DueDate) meta.push(`Due Date: ${doc.DueDate}`);
//   if (doc.BuyerReference) meta.push(`Reference: ${doc.BuyerReference}`);
//   pdf.setFont(undefined, "bold").text("Details", MARGIN_X, metaY);
//   pdf.setFont(undefined, "normal");
//   metaY += LINE_HEIGHT * 0.8;
//   meta.forEach(m => { pdf.text(m, MARGIN_X, metaY); metaY += LINE_HEIGHT * 0.75; });
//
//   // Lines table
//   const tableYStart = metaY + 4;
//   const columns: ColumnDef<UBLLine>[] = [
//     { header: "Pos", width: 12, accessor: l => l.ID },
//     { header: "Name", width: 40, accessor: l => l.Item?.Name || "" },
//     { header: "Description", width: 60, accessor: l => l.Item?.Description || "" },
//     { header: "Qty", width: 15, accessor: l => {
//         const q = getAmount(l); return q?.value?.toString() ?? ""; }, align: "right" },
//     { header: "Unit", width: 20, accessor: l => formatMoney(l.Price?.PriceAmount?.value), align: "right" },
//     { header: "Total", width: 25, accessor: l => formatMoney(l.LineExtensionAmount?.value), align: "right" }
//   ];
//
//   let cursorY = tableYStart;
//   pdf.setFont(undefined, "bold");
//   let currentX = MARGIN_X;
//   columns.forEach(col => {
//     if (currentX + col.width > MARGIN_X + contentWidth) return; // guard overflow
//     pdf.text(col.header, currentX + (col.align === "right" ? col.width - 1 : 0), cursorY, { align: col.align === "right" ? "right" : "left" });
//     currentX += col.width;
//   });
//   pdf.setFont(undefined, "normal");
//   cursorY += LINE_HEIGHT * 0.9;
//   pdf.setDrawColor(200);
//   pdf.line(MARGIN_X, cursorY - 4, MARGIN_X + contentWidth, cursorY - 4);
//
//   const lines: UBLLine[] = ("InvoiceLine" in doc) ? doc.InvoiceLine : doc.CreditNoteLine;
//   lines.sort((a,b) => (parseInt(a.ID) || 0) - (parseInt(b.ID) || 0));
//
//   lines.forEach(row => {
//     currentX = MARGIN_X;
//     if (cursorY > pdf.internal.pageSize.getHeight() - 40) { // new page if near bottom
//       pdf.addPage();
//       cursorY = MARGIN_Y;
//     }
//     columns.forEach(col => {
//       const text = col.accessor(row) ?? "";
//       const maxWidth = col.width - 2;
//       const splitted = pdf.splitTextToSize(text, maxWidth);
//       // Only single-line for simplicity; could expand row height if multi-line
//       const render = Array.isArray(splitted) ? splitted[0] : splitted;
//       pdf.text(render, currentX + (col.align === "right" ? col.width - 1 : 0), cursorY, { align: col.align === "right" ? "right" : "left" });
//       currentX += col.width;
//     });
//     cursorY += LINE_HEIGHT * 0.8;
//   });
//
//   // Totals section
//   const totalsY = Math.min(cursorY + 4, pdf.internal.pageSize.getHeight() - 40);
//   const totalsX = pageWidth - MARGIN_X - 60;
//   pdf.setFont(undefined, "bold").text("Totals", totalsX, totalsY);
//   pdf.setFont(undefined, "normal");
//   let ty = totalsY + LINE_HEIGHT * 0.8;
//   const exclusive = doc.LegalMonetaryTotal?.TaxExclusiveAmount?.value;
//   const taxTotal = doc.TaxTotal?.[0]?.TaxAmount?.value;
//   const payable = doc.LegalMonetaryTotal?.PayableAmount?.value;
//   const totalLines = [
//     `Subtotal: ${formatMoney(exclusive)}`,
//     `Tax: ${formatMoney(taxTotal)}`,
//     `Total: ${formatMoney(payable)}`
//   ];
//   totalLines.forEach(t => { pdf.text(t, totalsX, ty); ty += LINE_HEIGHT * 0.75; });
//
//   pdf.setFontSize(8).setTextColor(120);
//   pdf.text(`Generated ${new Date().toISOString()}`, MARGIN_X, pdf.internal.pageSize.getHeight() - 8);
//
//   return pdf;
// }
//
// export function downloadInvoicePdf(doc: UBLDoc) {
//   const pdf = generateInvoicePdf(doc);
//   const filename = `${getDocType(doc)}-${doc.ID || 'document'}.pdf`.replace(/\s+/g, '_');
//   pdf.save(filename);
// }
//
