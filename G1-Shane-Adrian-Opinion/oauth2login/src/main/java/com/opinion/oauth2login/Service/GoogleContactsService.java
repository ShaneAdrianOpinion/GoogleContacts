package com.opinion.oauth2login.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GoogleContactsService {

    private static final String CONTACTS_API_URL = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,phoneNumbers";
    private static final String CREATE_CONTACT_API_URL = "https://people.googleapis.com/v1/people:createContact";

    private final OAuth2AuthorizedClientService authorizedClientService;

    public GoogleContactsService(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    // ✅ Fetch the OAuth2 Access Token
    public String getAccessToken(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());

        if (client == null || client.getAccessToken() == null) {
            System.out.println("❌ No access token found!");
            return null;
        }

        String token = client.getAccessToken().getTokenValue();
        System.out.println("🔑 Access Token: " + token);  // ✅ Debugging line
        return token;
    }


    // ✅ Fetch All Contacts
    public List<Map<String, Object>> getAllContacts(OAuth2AuthenticationToken authentication) {
        String accessToken = getAccessToken(authentication);
        if (accessToken == null) {
            throw new RuntimeException("Access token not found");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(CONTACTS_API_URL, HttpMethod.GET, entity, Map.class);

        if (response.getBody() == null || !response.getBody().containsKey("connections")) {
            return List.of(); // Return empty list if no contacts found
        }

        List<Map<String, Object>> contacts = (List<Map<String, Object>>) response.getBody().get("connections");

        // ✅ Sort by first name to maintain order
        contacts.sort((c1, c2) -> {
            String name1 = ((List<Map<String, String>>) c1.get("names")).get(0).get("givenName");
            String name2 = ((List<Map<String, String>>) c2.get("names")).get(0).get("givenName");
            return name1.compareToIgnoreCase(name2);
        });

        return contacts;
    }

    // ✅ Add New Contact
    public String addContact(OAuth2AuthenticationToken authentication, String firstName, String lastName, String email, String phone) {
        String accessToken = getAccessToken(authentication);
        if (accessToken == null) {
            throw new RuntimeException("Access token not found");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ✅ Create JSON payload
        Map<String, Object> contact = new HashMap<>();
        contact.put("names", List.of(Map.of("givenName", firstName, "familyName", lastName)));
        if (email != null && !email.isEmpty()) {
            contact.put("emailAddresses", List.of(Map.of("value", email, "type", "home")));
        }
        if (phone != null && !phone.isEmpty()) {
            contact.put("phoneNumbers", List.of(Map.of("value", phone, "type", "mobile")));
        }

        // ✅ Convert Map to JSON for Debugging
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonPayload = objectMapper.writeValueAsString(contact);
            System.out.println("📌 Request Body: " + jsonPayload);  // 🔍 Print JSON Payload
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(contact, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    CREATE_CONTACT_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            System.out.println("✅ Response: " + response.getBody());
            return response.getBody();  // <-- Return response to controller
        } catch (HttpClientErrorException e) {
            System.out.println("❌ Error Response: " + e.getResponseBodyAsString());
            return "Error: " + e.getResponseBodyAsString(); // <-- Return error as string
        }
    }

    public String deleteContact(OAuth2AuthenticationToken authentication, String resourceName) {
        String accessToken = getAccessToken(authentication);
        if (accessToken == null) {
            throw new RuntimeException("Access token not found");
        }

        // Ensure resourceName starts with "people/"
        if (!resourceName.startsWith("people/")) {
            resourceName = "people/" + resourceName;
        }

        String deleteUrl = "https://people.googleapis.com/v1/" + resourceName + ":deleteContact";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    deleteUrl,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );
            System.out.println("✅ Contact Deleted: " + resourceName);
            return "Success";
        } catch (HttpClientErrorException e) {
            System.out.println("❌ Error Deleting Contact: " + e.getResponseBodyAsString());
            return "Error: " + e.getMessage();
        }
    }

    public String updateContact(OAuth2AuthenticationToken authentication, String resourceName, String firstName, String lastName, String email, String phone) {
        System.out.println("🔄 Updating Contact ID (Workaround): " + resourceName);

        deleteContact(authentication, resourceName);

        return addContact(authentication, firstName, lastName, email, phone);
    }

    public Map<String, Object> getContactById(OAuth2AuthenticationToken authentication, String id) {
        String accessToken = getAccessToken(authentication);
        if (accessToken == null) {
            throw new RuntimeException("Access token not found");
        }

        if (!id.startsWith("people/")) {
            id = "people/" + id;
        }

        String url = "https://people.googleapis.com/v1/" + id + "?personFields=names,emailAddresses,phoneNumbers";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

            if (response.getBody() == null) {
                return null;
            }

            return response.getBody();
        } catch (HttpClientErrorException e) {
            System.out.println("❌ Error Fetching Contact: " + e.getResponseBodyAsString());
            return null;
        }
    }





}
