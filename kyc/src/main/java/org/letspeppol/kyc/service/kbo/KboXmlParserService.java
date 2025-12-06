package org.letspeppol.kyc.service.kbo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KboXmlParserService {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final CompanyRepository companyRepository;
    private final KboBatchPersistenceService kboBatchPersistenceService;

    public void importEnterprises(InputStream xmlStream) {
        Objects.requireNonNull(xmlStream, "xmlStream must not be null");

        List<Company> batch = new ArrayList<>();
        List<String> peppolIdsToDelete = new ArrayList<>();

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            XMLStreamReader reader = factory.createXMLStreamReader(xmlStream);
            int totalEnterprises = 0;
            int totalValidEnterprises = 0;
            int totalSavedEnterprises = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "Enterprise".equals(reader.getLocalName())) {
                    totalEnterprises++;
                    EnterpriseData enterprise = readEnterprise(reader);

                    if (enterprise == null) {
                        continue; // skipped because it didn't meet criteria
                    }
                    totalValidEnterprises++;

                    String peppolId = buildPeppolIdFromNbr(enterprise.nbr);
                    String vatNumber = buildVatNumberFromNbr(enterprise.nbr);

                    if (enterprise.ended) {
                        boolean hasRegisteredDirector = companyRepository.existsRegisteredDirectorForPeppolId(peppolId);
                        if (hasRegisteredDirector) {
                            log.warn("Skipping deletion of company with Peppol ID {} because it has registered directors", peppolId);
                            continue;
                        }

                        log.info("Scheduling deletion of company with Peppol ID {} due to ended enterprise validity", peppolId);
                        peppolIdsToDelete.add(peppolId);
                        continue;
                    }

                    Company company = companyRepository.findWithDirectorsByPeppolId(peppolId).orElseGet(() -> {
                        if (enterprise.address == null) {
                            log.debug("Creating new company with Peppol ID {} without address", peppolId);
                            return new Company(peppolId, vatNumber, enterprise.name);
                        } else {
                            log.debug("Creating new company with Peppol ID {}", peppolId);
                            return new Company(peppolId, vatNumber, enterprise.name,
                                    enterprise.address.city, enterprise.address.postalCode,
                                    enterprise.address.street
                            );
                        }
                    });


                    boolean isNewCompany = company.getId() == null;
                    boolean companyChanged = false;
                    if (!isNewCompany) {
                        companyChanged = applyCompanyUpdates(company, enterprise);
                    }
                    boolean directorsChanged = syncDirectors(company, enterprise.directors);

                    if (isNewCompany || companyChanged || directorsChanged) {
                        batch.add(company);
                        totalSavedEnterprises++;
                    }

                    if (batch.size() >= DEFAULT_BATCH_SIZE) {
                        log.debug("Saving batch of {} companies to database", batch.size());
                        kboBatchPersistenceService.saveBatch(batch);
                        batch.clear();
                    }
                }
            }

            log.info("KboXmlParserService: Processed {} enterprises, of which {} were valid. Saved/updated {} companies.",
                    totalEnterprises, totalValidEnterprises, totalSavedEnterprises);

            if (!batch.isEmpty()) {
                kboBatchPersistenceService.saveBatch(batch);
            }

            if (!peppolIdsToDelete.isEmpty()) {
                log.info("Deleting {} companies that have ended enterprise validity", peppolIdsToDelete.size());
                kboBatchPersistenceService.deleteByPeppolIds(peppolIdsToDelete);
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Failed to parse KBO XML", e);
        }
    }

    private boolean applyCompanyUpdates(Company company, EnterpriseData enterprise) {
        boolean changed = false;

        if (!Objects.equals(company.getName(), enterprise.name)
            || (enterprise.address != null && (
                !Objects.equals(company.getCity(), enterprise.address.city)
                || !Objects.equals(company.getPostalCode(), enterprise.address.postalCode)
                || !Objects.equals(company.getStreet(), enterprise.address.street))
        )) {
            log.debug("Changes detected for company with Peppol ID {}", company.getPeppolId());
            company.setName(enterprise.name);
            if (enterprise.address != null) {
                company.setCity(enterprise.address.city);
                company.setPostalCode(enterprise.address.postalCode);
                company.setStreet(enterprise.address.street);
            }
            changed = true;
        }
        return changed;
    }

    private boolean syncDirectors(Company company, List<FunctionData> functionDataList) {
        List<Director> currentDirectors = company.getDirectors();
        List<Director> protectedDirectors = extractProtectedDirectors(currentDirectors);
        List<Director> targetDirectors = new ArrayList<>(protectedDirectors);

        Map<String, Director> reusableDirectors = new LinkedHashMap<>();
        for (Director director : currentDirectors) {
            if (!director.isRegistered()) {
                reusableDirectors.putIfAbsent(director.getName(), director);
            }
        }

        for (String fullName : toActiveDirectorNames(functionDataList)) {
            Director director = reusableDirectors.remove(fullName);
            if (director == null) {
                director = new Director(fullName, company);
            }
            targetDirectors.add(director);
        }

        if (directorsEqual(currentDirectors, targetDirectors)) {
            return false;
        }

        currentDirectors.clear();
        currentDirectors.addAll(targetDirectors);
        return true;
    }

    private List<Director> extractProtectedDirectors(List<Director> existingDirectors) {
        return existingDirectors.stream()
                .filter(Director::isRegistered)
                .toList();
    }

    private Set<String> toActiveDirectorNames(List<FunctionData> functionDataList) {
        Set<String> activeDirectorNames = new LinkedHashSet<>();

        for (FunctionData d : functionDataList) {
            String fullName = (d.firstName != null && !d.firstName.isBlank())
                    ? d.firstName + " " + d.lastName
                    : d.lastName;
            if (fullName != null && !fullName.isBlank()) {
                activeDirectorNames.add(fullName);
            }
        }

        return activeDirectorNames;
    }

    private boolean directorsEqual(List<Director> currentDirectors, List<Director> targetDirectors) {
        if (currentDirectors.size() != targetDirectors.size()) {
            return false;
        }

        Comparator<Director> comparator = Comparator.comparing(Director::isRegistered).reversed()
                .thenComparing(Director::getName, Comparator.nullsFirst(String::compareTo));

        List<Director> currentSorted = new ArrayList<>(currentDirectors);
        currentSorted.sort(comparator);
        List<Director> targetSorted = new ArrayList<>(targetDirectors);
        targetSorted.sort(comparator);

        for (int i = 0; i < currentSorted.size(); i++) {
            Director current = currentSorted.get(i);
            Director target = targetSorted.get(i);
            if (!Objects.equals(current.getName(), target.getName())) {
                return false;
            }
            if (current.isRegistered() != target.isRegistered()) {
                return false;
            }
        }

        return true;
    }

    private EnterpriseData readEnterprise(XMLStreamReader reader) throws XMLStreamException {
        String nbr = null;

        AddressData address = null;
        String denominationName = null;

        List<FunctionData> directors = new ArrayList<>();
        boolean enterpriseEnded = false;
        int depth = 0;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                depth++;
                // System.out.println(localName + " " + depth); // debug

                if ("Nbr".equals(localName)) {
                    nbr = readSimpleTextElement(reader);
                    depth--;
                } else if ("Addresses".equals(localName)) {
                    AddressData lastAddress = readAddresses(reader);
                    if (lastAddress != null) {
                        address = lastAddress;
                    }
                    depth--;
                } else if ("Denominations".equals(localName)) {
                    String nameWithoutEnd = readDenominations(reader);
                    if (nameWithoutEnd != null && (denominationName == null || denominationName.isBlank())) {
                        denominationName = nameWithoutEnd;
                    }
                    depth--;
                } else if ("Functions".equals(localName)) {
                    directors.addAll(readFunctions(reader));
                    depth--;
                } else if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    if (depth == 1 && flags.hasEnd) {
                        enterpriseEnded = true;
                    }
                    depth--;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Enterprise".equals(reader.getLocalName())) {
                    break;
                }
                depth = Math.max(depth - 1, 0);
            }
        }

        if (nbr == null || nbr.isBlank()) {
            log.debug("Skipping enterprise without NBR");
            return null;
        }
        if (address != null && address.countryCode != null && !address.countryCode.equals("150")) {
            log.debug("Skipping enterprise {} not located in Belgium (country code 150)", nbr);
            return null;
        }
//        if (address == null) {
//            log.debug("Skipping enterprise {} without address", nbr);
//            return null;
//        }
        if (directors.isEmpty()) {
            log.debug("Skipping enterprise {} without directors", nbr);
            return null;
        }
        if (denominationName == null || denominationName.isBlank()) {
            log.debug("Skipping enterprise {} without denominations", nbr);
            return null;
        }

        return new EnterpriseData(nbr, denominationName, address, directors, enterpriseEnded);
    }

    private AddressData readAddresses(XMLStreamReader reader) throws XMLStreamException {
        AddressData best = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "Address".equals(reader.getLocalName())) {
                AddressData candidate = readAddress(reader);
                if (candidate != null) {
                    best = candidate; // prefer the last usable one
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Addresses".equals(reader.getLocalName())) {
                break;
            }
        }

        return best;
    }

    private AddressData readAddress(XMLStreamReader reader) throws XMLStreamException {
        AddressData best = null;
        boolean addressHasEnd = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("AddressCoding".equals(localName)) {
                    AddressData candidate = readAddressCoding(reader);
                    if (candidate != null && !addressHasEnd) {
                        best = candidate;
                    }
                } else if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    addressHasEnd = flags.hasEnd;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Address".equals(reader.getLocalName())) {
                break;
            }
        }

        if (addressHasEnd) {
            return null;
        }

        return best;
    }

    private AddressData readAddressCoding(XMLStreamReader reader) throws XMLStreamException {
        String street = null;
        String city = null;
        String houseNumber = null;
        String postbox = null;
        String postalCode = null;
        String countryCode = null;
        boolean codingHasEnd = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Descriptions".equals(localName)) {
                    AddressDescription selection = readDescriptions(reader);
                    if (selection != null) {
                        street = selection.streetName;
                        city = selection.communityName;
                    }
                } else if ("HouseNbr".equals(localName)) {
                    houseNumber = readSimpleTextElement(reader);
                } else if ("PostBox".equals(localName)) {
                    postbox = readSimpleTextElement(reader);
                } else if ("PostCode".equals(localName)) {
                    postalCode = readSimpleTextElement(reader);
                } else if ("CountryCode".equals(localName)) {
                    countryCode = readSimpleTextElement(reader);
                } else if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    codingHasEnd = flags.hasEnd;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "AddressCoding".equals(reader.getLocalName())) {
                break;
            }
        }

        if (street != null && !street.isBlank()) {
            StringBuilder sb = new StringBuilder(street);
            if (houseNumber != null && !houseNumber.isBlank()) {
                sb.append(" ").append(houseNumber.trim());
            }
            if (postbox != null && !postbox.isBlank()) {
                sb.append(" ").append(postbox.trim());
            }
            street = sb.toString();
        }

        if (codingHasEnd || street == null || city == null || postalCode == null) {
            return null;
        }

        return new AddressData(street, city, postalCode, countryCode);
    }

    private AddressDescription readDescriptions(XMLStreamReader reader) throws XMLStreamException {
        AddressDescription preferred = null;
        AddressDescription first = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "Description".equals(reader.getLocalName())) {
                AddressDescription sel = readDescription(reader);
                if (first == null) {
                    first = sel;
                }
                if ("2".equals(sel.language)) {
                    preferred = sel;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Descriptions".equals(reader.getLocalName())) {
                break;
            }
        }

        return preferred != null ? preferred : first;
    }

    private AddressDescription readDescription(XMLStreamReader reader) throws XMLStreamException {
        String language = null;
        String streetName = null;
        String communityName = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Language".equals(localName)) {
                    language = readSimpleTextElement(reader);
                } else if ("StreetName".equals(localName)) {
                    streetName = readSimpleTextElement(reader);
                } else if ("CommunityName".equals(localName)) {
                    communityName = readSimpleTextElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Description".equals(reader.getLocalName())) {
                break;
            }
        }

        return new AddressDescription(language, streetName, communityName);
    }

    private String readDenominations(XMLStreamReader reader) throws XMLStreamException {
        String chosenName = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "Denomination".equals(reader.getLocalName())) {
                String name = readDenomination(reader);
                if (name != null) {
                    chosenName = name;
                    break;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Denominations".equals(reader.getLocalName())) {
                break;
            }
        }

        while (reader.hasNext() && !(reader.getEventType() == XMLStreamConstants.END_ELEMENT && "Denominations".equals(reader.getLocalName()))) {
            int event = reader.next();
            if (event == XMLStreamConstants.END_ELEMENT && "Denominations".equals(reader.getLocalName())) {
                break;
            }
        }

        return chosenName;
    }

    private String readDenomination(XMLStreamReader reader) throws XMLStreamException {
        String type = null;
        String language = null;
        String name = null;
        boolean hasEnd = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Type".equals(localName)) {
                    type = readSimpleTextElement(reader);
                } else if ("Language".equals(localName)) {
                    language = readSimpleTextElement(reader);
                } else if ("Name".equals(localName)) {
                    name = readSimpleTextElement(reader);
                } else if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    hasEnd = flags.hasEnd;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Denomination".equals(reader.getLocalName())) {
                break;
            }
        }

        if (name == null || name.isBlank()) {
            return null;
        }

        if (!hasEnd) {
            return name;
        }

        return null;
    }

    private List<FunctionData> readFunctions(XMLStreamReader reader) throws XMLStreamException {
        List<FunctionData> directors = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "Function".equals(reader.getLocalName())) {
                FunctionData director = readFunction(reader);
                if (director != null) {
                    directors.add(director);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Functions".equals(reader.getLocalName())) {
                break;
            }
        }

        return directors;
    }

    private FunctionData readFunction(XMLStreamReader reader) throws XMLStreamException {
        boolean hasEnd = false;
        String firstName = null;
        String lastName = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    hasEnd = flags.hasEnd;
                } else if ("HeldByPerson".equals(localName)) {
                    HeldByPersonData heldBy = readHeldByPerson(reader);
                    firstName = heldBy.firstName;
                    lastName = heldBy.lastName;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Function".equals(reader.getLocalName())) {
                break;
            }
        }

        if (hasEnd) {
            return null;
        }

        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return null;
        }

        return new FunctionData(firstName, lastName);
    }

    private ValidityFlags readValidity(XMLStreamReader reader) throws XMLStreamException {
        boolean hasEnd = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("End".equals(localName)) {
                    String end = readSimpleTextElement(reader);
                    if (end != null && !end.isBlank()) {
                        hasEnd = true;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Validity".equals(reader.getLocalName())) {
                break;
            }
        }

        return new ValidityFlags(hasEnd);
    }

    private HeldByPersonData readHeldByPerson(XMLStreamReader reader) throws XMLStreamException {
        String firstName = null;
        String lastName = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("FirstName".equals(localName)) {
                    firstName = readSimpleTextElement(reader);
                } else if ("Name".equals(localName)) {
                    lastName = readSimpleTextElement(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "HeldByPerson".equals(reader.getLocalName())) {
                break;
            }
        }

        return new HeldByPersonData(firstName, lastName);
    }

    private String readSimpleTextElement(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder sb = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                sb.append(reader.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private String buildVatNumberFromNbr(String nbr) {
        if (nbr.length() < 10) {
            return "BE0" + nbr;
        }
        return "BE" + nbr;
    }

    private String buildPeppolIdFromNbr(String nbr) {
        if (nbr.length() < 10) {
            return "0208:0" + nbr;
        }
        return "0208:" + nbr;
    }

    private record AddressData(String street, String city, String postalCode, String countryCode) {}

    private record AddressDescription(String language, String streetName, String communityName) {}

    private record FunctionData(String firstName, String lastName) {}

    private record HeldByPersonData(String firstName, String lastName) {}

    private record EnterpriseData(String nbr, String name, AddressData address, List<FunctionData> directors, boolean ended) {}

    private record ValidityFlags(boolean hasEnd) {}
}
