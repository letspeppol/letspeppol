<?xml version="1.0" encoding="UTF-8"?>
<!--
  Minimal UBL CreditNote -> HTML XSLT.
  Mirrors the invoice renderer. Uses local-name() to be namespace-agnostic.
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- XSLT parameters used to control draft/proforma rendering -->
  <xsl:param name="renderNumber" select="'true'"/>
  <xsl:param name="watermarkText" select="''"/>

  <xsl:output method="xml" encoding="UTF-8" omit-xml-declaration="yes"
              doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"
              doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"/>
  <xsl:strip-space elements="*"/>

  <xsl:decimal-format name="money" decimal-separator="." grouping-separator=","/>

  <xsl:template match="/">
    <html xmlns="http://www.w3.org/1999/xhtml">
    <head>
      <meta charset="UTF-8" />
      <style type="text/css">
        body { font-family: sans-serif; font-size: 12px; color: #111; }
        h1 { font-size: 18px; margin: 0 0 12px 0; }
        h2 { font-size: 14px; margin: 14px 0 6px 0; }

        table { width: 100%; border-collapse: collapse; margin-top: 8px; }
        th, td { border-bottom: 1px solid #ddd; padding: 6px 4px; }
        th { text-align: left; background: #f5f5f5; }

        .muted { color: #666; }
        .amount { text-align: right; white-space: nowrap; }

        /* Layout helpers (avoid flex - not consistently supported by OpenHTMLtoPDF) */
        .meta td { border: none; padding: 0; vertical-align: top; }

        /* Parties: explicitly no margin/padding/border */
        table.parties { margin: 0; margin-top: 20px; margin-bottom: 20px; border: none; }
        table.parties td { border: none; padding: 0; vertical-align: top; }
        .text-right { text-align: right; }
        .parties div { clear: both; display: block; }

        .totals { margin-top: 12px; margin-left: auto; width: 50%; }
        .totals td { border: none; padding: 4px; }

        .note {
          clear: both;
          float: left;
        }

        /* Watermark for draft/proforma */
        .watermark {
          position: fixed;
          left: 50%;
          top: 0;
          transform: translate(-50%, 0%);
          font-size: 36px;
          font-weight: bold;
          color: #E0E0E0;
          z-index: 0;
          white-space: nowrap;
          letter-spacing: 2px;
        }
        .content { position: relative; z-index: 1; }
      </style>
    </head>
    <body>

      <xsl:if test="string-length(normalize-space($watermarkText)) &gt; 0">
        <div class="watermark"><xsl:value-of select="$watermarkText"/></div>
      </xsl:if>

      <div class="content">

      <h1>Credit note</h1>

      <table class="meta">
        <tr>
          <td style="width: 50%">
            <xsl:if test="$renderNumber = 'true'">
              <div><span class="muted">Number: </span> <strong><xsl:value-of select="normalize-space(/*/*[local-name()='ID'][1])"/></strong></div>
            </xsl:if>
            <div><span class="muted">Issue date: </span> <xsl:value-of select="normalize-space(/*/*[local-name()='IssueDate'][1])"/></div>

            <!-- Referenced invoice(s) -->
            <xsl:variable name="invRef" select="normalize-space((/*/*[local-name()='BillingReference']/*[local-name()='InvoiceDocumentReference']/*[local-name()='ID'])[1])"/>
            <xsl:if test="string-length($invRef) &gt; 0">
              <div><span class="muted">Invoice reference: </span> <xsl:value-of select="$invRef"/></div>
            </xsl:if>

            <xsl:variable name="buyerReference" select="normalize-space((/*/*[local-name()='BuyerReference'])[1])"/>
            <xsl:if test="string-length($buyerReference) &gt; 0">
              <div><span class="muted">Buyer reference: </span> <xsl:value-of select="$buyerReference"/></div>
            </xsl:if>

            <xsl:variable name="orderReferenceId" select="normalize-space((/*/*[local-name()='OrderReference']/*[local-name()='ID'])[1])"/>
            <xsl:if test="string-length($orderReferenceId) &gt; 0">
              <div><span class="muted">Order reference: </span> <xsl:value-of select="$orderReferenceId"/></div>
            </xsl:if>
          </td>
          <td style="width: 50%" class="text-right">
            <xsl:variable name="paymentAccountId" select="normalize-space((./*/*[local-name()='PaymentMeans']/*[local-name()='PayeeFinancialAccount']/*[local-name()='ID'])[1])"/>
            <xsl:variable name="paymentAccountName" select="normalize-space((./*/*[local-name()='PaymentMeans']/*[local-name()='PayeeFinancialAccount']/*[local-name()='Name'])[1])"/>
            <xsl:variable name="paymentServiceProviderId" select="normalize-space((./*/*[local-name()='PaymentMeans']/*[local-name()='PayeeFinancialAccount']/*[local-name()='FinancialInstitutionBranch']/*[local-name()='ID'])[1])"/>
            <xsl:variable name="paymentId" select="normalize-space((./*/*[local-name()='PaymentMeans']/*[local-name()='PaymentID'])[1])"/>

            <strong>Payment Info</strong>
            <xsl:if test="string-length($paymentAccountId) &gt; 0">
              <div><xsl:value-of select="$paymentAccountId"/></div>
            </xsl:if>
            <xsl:if test="string-length($paymentServiceProviderId) &gt; 0">
              <div><xsl:value-of select="$paymentServiceProviderId"/></div>
            </xsl:if>
            <xsl:if test="string-length($paymentAccountName) &gt; 0">
              <div><xsl:value-of select="$paymentAccountName"/></div>
            </xsl:if>
            <xsl:if test="string-length($paymentId) &gt; 0">
              <div><span class="muted">Ref: </span> <xsl:value-of select="$paymentId"/></div>
            </xsl:if>
          </td>
        </tr>
      </table>

      <table class="parties">
        <tr>
          <td style="width: 50%">
            <div><strong><xsl:value-of select="normalize-space((/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='PartyName']/*[local-name()='Name'])[1])"/></strong></div>

            <xsl:variable name="customerCompanyId" select="normalize-space((/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='PartyTaxScheme']/*[local-name()='CompanyID'])[1])"/>
            <xsl:if test="string-length($customerCompanyId) &gt; 0">
              <div class="muted"><xsl:value-of select="$customerCompanyId"/></div>
            </xsl:if>

            <xsl:call-template name="render-address">
              <xsl:with-param name="address" select="(/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='PostalAddress'])[1]"/>
            </xsl:call-template>
          </td>

          <td style="width: 50%" class="text-right">
            <div><strong><xsl:value-of select="normalize-space((/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PartyName']/*[local-name()='Name'])[1])"/></strong></div>

            <xsl:variable name="supplierCompanyId" select="normalize-space((/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PartyTaxScheme']/*[local-name()='CompanyID'])[1])"/>
            <xsl:if test="string-length($supplierCompanyId) &gt; 0">
              <div class="muted"><xsl:value-of select="$supplierCompanyId"/></div>
            </xsl:if>

            <xsl:call-template name="render-address">
              <xsl:with-param name="address" select="(/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PostalAddress'])[1]"/>
            </xsl:call-template>
          </td>
        </tr>
      </table>

      <h2>Lines</h2>
      <table>
        <thead>
          <tr>
            <th style="width: 45%">Description</th>
            <th style="width: 15%">Qty</th>
            <th style="width: 15%" class="amount">Unit price</th>
            <th style="width: 10%" class="amount">Tax</th>
            <th style="width: 15%" class="amount">Line total</th>
          </tr>
        </thead>
        <tbody>
          <xsl:for-each select="/*/*[local-name()='CreditNoteLine']">
            <tr>
              <td>
                <xsl:variable name="lineName" select="normalize-space((./*[local-name()='Item']/*[local-name()='Name'])[1])"/>
                <xsl:variable name="lineDescription" select="normalize-space((./*[local-name()='Item']/*[local-name()='Description'])[1])"/>
                <xsl:variable name="sellersItemId" select="normalize-space((./*[local-name()='Item']/*[local-name()='SellersItemIdentification']/*[local-name()='ID'])[1])"/>

                <xsl:if test="string-length($lineName) &gt; 0">
                  <div><strong><xsl:value-of select="$lineName"/></strong></div>
                </xsl:if>
                <xsl:if test="string-length($lineDescription) &gt; 0">
                  <div class="muted"><xsl:value-of select="$lineDescription"/></div>
                </xsl:if>
                <xsl:if test="string-length($sellersItemId) &gt; 0">
                  <div class="muted"><xsl:value-of select="$sellersItemId"/></div>
                </xsl:if>

                <xsl:for-each select="./*[local-name()='Item']/*[local-name()='AdditionalItemProperty']">
                  <xsl:variable name="propName" select="normalize-space((./*[local-name()='Name'])[1])"/>
                  <xsl:variable name="propValue" select="normalize-space((./*[local-name()='Value'])[1])"/>

                  <xsl:if test="string-length($propName) &gt; 0 or string-length($propValue) &gt; 0">
                    <div class="muted">
                      <xsl:choose>
                        <xsl:when test="string-length($propName) &gt; 0 and string-length($propValue) &gt; 0">
                          <xsl:value-of select="$propName"/>
                          <xsl:text>: </xsl:text>
                          <xsl:value-of select="$propValue"/>
                        </xsl:when>
                        <xsl:when test="string-length($propName) &gt; 0">
                          <xsl:value-of select="$propName"/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select="$propValue"/>
                        </xsl:otherwise>
                      </xsl:choose>
                    </div>
                  </xsl:if>
                </xsl:for-each>
              </td>
              <td>
                <xsl:value-of select="normalize-space((./*[local-name()='CreditedQuantity'])[1])"/>
              </td>
              <td class="amount">
                <xsl:call-template name="format-eur">
                  <xsl:with-param name="value" select="normalize-space((./*[local-name()='Price']/*[local-name()='PriceAmount'])[1])"/>
                </xsl:call-template>
              </td>
              <td class="amount">
                <xsl:variable name="tax" select="normalize-space((./*[local-name()='Item']/*[local-name()='ClassifiedTaxCategory']/*[local-name()='Percent'])[1])"/>
                <xsl:if test="string-length($tax) &gt; 0">
                  <xsl:value-of select="$tax"/> <span>&#37;</span>
                </xsl:if>
              </td>
              <td class="amount">
                <xsl:call-template name="format-eur">
                  <xsl:with-param name="value" select="normalize-space((./*[local-name()='LineExtensionAmount'])[1])"/>
                </xsl:call-template>
              </td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table>

      <table class="totals">
        <tr>
          <td>Tax exclusive</td>
          <td class="amount">
            <xsl:call-template name="format-eur">
              <xsl:with-param name="value" select="normalize-space((/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount'])[1])"/>
            </xsl:call-template>
          </td>
        </tr>
        <tr>
          <td>Tax amount</td>
          <td class="amount">
            <xsl:call-template name="format-eur">
              <xsl:with-param name="value" select="normalize-space((/*/*[local-name()='TaxTotal']/*[local-name()='TaxAmount'])[1])"/>
            </xsl:call-template>
          </td>
        </tr>
        <tr>
          <td><strong>Payable amount</strong></td>
          <td class="amount"><strong>
            <xsl:call-template name="format-eur">
              <xsl:with-param name="value" select="normalize-space((/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount'])[1])"/>
            </xsl:call-template>
          </strong></td>
        </tr>
      </table>

      </div>

      <xsl:variable name="note" select="./*/*[local-name()='Note'][1]"/>
      <xsl:if test="string-length($note) &gt; 0">
        <strong>Note</strong>
        <span class="note"><xsl:value-of select="$note"/></span>
      </xsl:if>

    </body>
    </html>
  </xsl:template>

  <xsl:template name="render-address">
    <xsl:param name="address"/>
    <xsl:variable name="addr" select="$address"/>

    <xsl:if test="$addr">
      <xsl:variable name="street" select="normalize-space(($addr/*[local-name()='StreetName'])[1])"/>
      <xsl:variable name="additional" select="normalize-space(($addr/*[local-name()='AdditionalStreetName'])[1])"/>
      <xsl:variable name="city" select="normalize-space(($addr/*[local-name()='CityName'])[1])"/>
      <xsl:variable name="zip" select="normalize-space(($addr/*[local-name()='PostalZone'])[1])"/>
      <xsl:variable name="country" select="normalize-space(($addr/*[local-name()='Country']/*[local-name()='IdentificationCode'])[1])"/>

      <xsl:if test="string-length($street) &gt; 0 or string-length($additional) &gt; 0 or string-length($city) &gt; 0 or string-length($zip) &gt; 0 or string-length($country) &gt; 0">
        <xsl:if test="string-length($street) &gt; 0 or string-length($additional) &gt; 0">
          <div>
            <xsl:value-of select="$street"/>
            <xsl:if test="string-length($street) &gt; 0 and string-length($additional) &gt; 0"><xsl:text> </xsl:text></xsl:if>
            <xsl:value-of select="$additional"/>
          </div>
        </xsl:if>

        <xsl:if test="string-length($zip) &gt; 0 or string-length($city) &gt; 0">
          <div>
            <xsl:value-of select="$zip"/>
            <xsl:if test="string-length($zip) &gt; 0 and string-length($city) &gt; 0"><xsl:text> </xsl:text></xsl:if>
            <xsl:value-of select="$city"/>
          </div>
        </xsl:if>

        <xsl:if test="string-length($country) &gt; 0">
          <div><xsl:value-of select="$country"/></div>
        </xsl:if>
      </xsl:if>
    </xsl:if>
  </xsl:template>

  <xsl:template name="format-eur">
    <xsl:param name="value"/>
    <xsl:choose>
      <xsl:when test="string-length(normalize-space($value)) &gt; 0">
        <xsl:text>€</xsl:text>
        <xsl:value-of select="format-number(number($value), '0.00', 'money')"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:text>€0.00</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>

