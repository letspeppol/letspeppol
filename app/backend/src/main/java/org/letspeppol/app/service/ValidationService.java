package org.letspeppol.app.service;

import com.helger.ddd.DocumentDetails;
import com.helger.ddd.DocumentDetailsDeterminator;
import com.helger.ddd.model.DDDSyntaxList;
import com.helger.ddd.model.DDDValueProviderList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.phive.api.execute.ValidationExecutionManager;
import com.helger.phive.api.executorset.IValidationExecutorSet;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.validity.IValidityDeterminator;
import com.helger.phive.en16931.EN16931Validation;
import com.helger.phive.peppol.PeppolValidation;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.ubl.UBLValidation;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;
import com.helger.io.resource.inmemory.ReadableResourceString;
import com.helger.diagnostics.error.IError;
import org.letspeppol.app.dto.ValidationErrorDto;
import org.letspeppol.app.dto.ValidationResultDto;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ValidationService {
    private static final ValidationExecutorSetRegistry<IValidationSourceXML> REGISTRY;
    static {
        REGISTRY = new ValidationExecutorSetRegistry<>();
        UBLValidation.initUBLAllVersions(REGISTRY);
        EN16931Validation.initEN16931(REGISTRY);
        PeppolValidation.initStandard(REGISTRY);
    }

    public ValidationResultDto validateUblXml(String ublXml) {
        ReadableResourceString rr = new ReadableResourceString(ublXml, StandardCharsets.UTF_8);
        IValidationSourceXML source = ValidationSourceXML.create(rr);

        // Robustly get the document element for DDD detection
        org.w3c.dom.Node node = source.getNode();
        org.w3c.dom.Element docElem = null;
        if (node instanceof org.w3c.dom.Document) {
            docElem = ((org.w3c.dom.Document) node).getDocumentElement();
        } else if (node instanceof org.w3c.dom.Element) {
            docElem = (org.w3c.dom.Element) node;
        }
        if (docElem == null) {
            return errorResponse("Could not find document element in XML");
        }

        DocumentDetailsDeterminator ddd = new DocumentDetailsDeterminator(
                DDDSyntaxList.getDefaultSyntaxList(),
                DDDValueProviderList.getDefaultValueProviderList());
        DocumentDetails details = ddd.findDocumentDetails(docElem);

        DVRCoordinate vesId = null;
        String detectedVESID = null;
        if (details != null && details.getVESID() != null) {
            detectedVESID = details.getVESID();
            vesId = toDVRCoordinate(detectedVESID);
        }
        if (vesId == null) {
            // Fallback to Peppol Invoice V3 ruleset (May 2025 release)
            vesId = PeppolValidation2025_05.VID_OPENPEPPOL_INVOICE_UBL_V3;
        }
        IValidationExecutorSet<IValidationSourceXML> ves = REGISTRY.getOfID(vesId);
        if (ves == null) {
            return errorResponse("Validation set not found for VES ID: " + vesId);
        }

        final ValidationResultList results = ValidationExecutionManager.executeValidation(
                IValidityDeterminator.createDefault(),
                ves,
                source
        );

        var errorList = results.getAllErrors();
        var errors = new ArrayList<ValidationErrorDto>();
        errorList.forEach(err -> errors.add(toErrorMap(err)));

        boolean isValid = results.containsNoError();

        return new ValidationResultDto(
                isValid,
                errorList.size(),
                errors,
                vesId != null ? vesId.toString() : "unknown",
                detectedVESID != null ? detectedVESID : null
        );
    }

    private DVRCoordinate toDVRCoordinate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // Expected pattern group:artifact:version
        String[] parts = raw.trim().split(":" );
        if (parts.length == 3) {
            try {
                IValidationExecutorSet<IValidationSourceXML> latestActiveVersion = REGISTRY.getLatestActiveVersion(parts[0], parts[1], null, null);
                if (latestActiveVersion != null) {
                    return latestActiveVersion.getID();
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private ValidationResultDto errorResponse(String message) {
        return new ValidationResultDto(
                false,
                1,
                List.of(new ValidationErrorDto(
                        0,
                        0,
                        message,
                        "ERROR"
                )),
                "unknown",
                null
        );
    }

    private ValidationErrorDto toErrorMap(IError err) {
        return new ValidationErrorDto(
                err.getErrorLocation() != null ? err.getErrorLocation().getColumnNumber() : 0, // Access always present location defensively; suppress warning by unconditional access if not null
                err.getErrorLocation() != null ? err.getErrorLocation().getLineNumber() : 0, // Access always present location defensively; suppress warning by unconditional access if not null
                err.getErrorText(Locale.ENGLISH),
                err.getErrorLevel().getID()
        );
    }
}
