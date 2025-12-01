package org.letspeppol.kyc.service.kbo;

import lombok.RequiredArgsConstructor;
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


@Service
@RequiredArgsConstructor
public class KboXmlParserService {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final CompanyRepository companyRepository;

    public List<Company> importEnterprises(InputStream xmlStream) {
        Objects.requireNonNull(xmlStream, "xmlStream must not be null");

        List<Company> batch = new ArrayList<>();

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            XMLStreamReader reader = factory.createXMLStreamReader(xmlStream);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "Enterprise".equals(reader.getLocalName())) {
                    EnterpriseData enterprise = readEnterprise(reader);

                    if (enterprise == null) {
                        continue; // skipped because it didn't meet criteria
                    }

                    String peppolId = buildPeppolIdFromNbr(enterprise.nbr);

                    Company company = companyRepository.findByPeppolId(peppolId)
                            .orElseGet(() -> new Company(peppolId, enterprise.nbr, enterprise.name,
                                    enterprise.address.city, enterprise.address.postalCode,
                                    enterprise.address.street, enterprise.address.houseNumber));

                    company.setVatNumber(enterprise.nbr);
                    company.setName(enterprise.name);
                    company.setCity(enterprise.address.city);
                    company.setPostalCode(enterprise.address.postalCode);
                    company.setStreet(enterprise.address.street);
                    company.setHouseNumber(enterprise.address.houseNumber);

                    // Sync directors with active ones from KBO, but keep any registered directors
                    syncDirectors(company, enterprise.directors);

                    batch.add(company);

                    if (batch.size() >= DEFAULT_BATCH_SIZE) {
                        companyRepository.saveAll(new ArrayList<>(batch));
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                companyRepository.saveAll(batch);
            }
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Failed to parse KBO XML", e);
        }

        return batch;
    }

    private void syncDirectors(Company company, List<FunctionData> functionDataList) {
        List<Director> existingDirectors = new ArrayList<>(company.getDirectors());

        List<Director> protectedDirectors = extractProtectedDirectors(existingDirectors);
        Set<String> activeDirectorNames = toActiveDirectorNames(functionDataList);

        company.getDirectors().clear();
        company.getDirectors().addAll(protectedDirectors);

        mergeActiveDirectors(company, activeDirectorNames);
    }

    private List<Director> extractProtectedDirectors(List<Director> existingDirectors) {
        return existingDirectors.stream()
                .filter(Director::isRegistered)
                .toList();
    }

    private Set<String> toActiveDirectorNames(List<FunctionData> functionDataList) {
        Set<String> activeDirectorNames = new HashSet<>();

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

    private void mergeActiveDirectors(Company company, Set<String> activeDirectorNames) {
        Set<String> existingNames = new HashSet<>();
        for (Director d : company.getDirectors()) {
            existingNames.add(d.getName());
        }

        for (String fullName : activeDirectorNames) {
            if (!existingNames.contains(fullName)) {
                Director director = new Director(fullName, company);
                company.getDirectors().add(director);
            }
        }
    }

    private EnterpriseData readEnterprise(XMLStreamReader reader) throws XMLStreamException {
        String nbr = null;

        AddressData address = null;
        String denominationName = null;

        List<FunctionData> directors = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Nbr".equals(localName)) {
                    nbr = readSimpleTextElement(reader);
                } else if ("Addresses".equals(localName)) {
                    AddressData lastAddress = readAddresses(reader);
                    if (lastAddress != null) {
                        address = lastAddress;
                    }
                } else if ("Denominations".equals(localName)) {
                    String nameWithoutEnd = readDenominations(reader);
                    if (nameWithoutEnd != null && (denominationName == null || denominationName.isBlank())) {
                        denominationName = nameWithoutEnd;
                    }
                } else if ("Functions".equals(localName)) {
                    directors.addAll(readFunctions(reader));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Enterprise".equals(reader.getLocalName())) {
                break;
            }
        }

        if (nbr == null || nbr.isBlank()) {
            return null;
        }
        if (address == null) {
            return null;
        }
        if (directors.isEmpty()) {
            return null;
        }
        if (denominationName == null || denominationName.isBlank()) {
            return null;
        }

        return new EnterpriseData(nbr, denominationName, address, directors);
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
        String postalCode = null;
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
                } else if ("PostCode".equals(localName)) {
                    postalCode = readSimpleTextElement(reader);
                } else if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    codingHasEnd = flags.hasEnd;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "AddressCoding".equals(reader.getLocalName())) {
                break;
            }
        }

        if (codingHasEnd || street == null || city == null || postalCode == null) {
            return null;
        }

        return new AddressData(street, city, postalCode, houseNumber);
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

        if ("001".equals(type) && "2".equals(language) && !hasEnd) {
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
        boolean hasBegin = false;
        boolean hasEnd = false;
        String firstName = null;
        String lastName = null;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Validity".equals(localName)) {
                    ValidityFlags flags = readValidity(reader);
                    hasBegin = flags.hasBegin;
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

        boolean active = hasBegin && !hasEnd;
        if (!active) {
            return null;
        }

        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return null;
        }

        return new FunctionData(firstName, lastName);
    }

    private ValidityFlags readValidity(XMLStreamReader reader) throws XMLStreamException {
        boolean hasBegin = false;
        boolean hasEnd = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();
                if ("Begin".equals(localName)) {
                    String begin = readSimpleTextElement(reader);
                    if (begin != null && !begin.isBlank()) {
                        hasBegin = true;
                    }
                } else if ("End".equals(localName)) {
                    String end = readSimpleTextElement(reader);
                    if (end != null && !end.isBlank()) {
                        hasEnd = true;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "Validity".equals(reader.getLocalName())) {
                break;
            }
        }

        return new ValidityFlags(hasBegin, hasEnd);
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

    private String buildPeppolIdFromNbr(String nbr) {
        return "BE" + nbr;
    }

    private record AddressData(String street, String city, String postalCode, String houseNumber) {}

    private record AddressDescription(String language, String streetName, String communityName) {}

    private record FunctionData(String firstName, String lastName) {}

    private record HeldByPersonData(String firstName, String lastName) {}

    private record EnterpriseData(String nbr, String name, AddressData address, List<FunctionData> directors) {}

    private record ValidityFlags(boolean hasBegin, boolean hasEnd) {}
}
