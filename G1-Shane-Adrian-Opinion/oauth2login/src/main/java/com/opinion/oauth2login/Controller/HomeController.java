package com.opinion.oauth2login.Controller;

import com.opinion.oauth2login.Service.GoogleContactsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final GoogleContactsService googleContactsService;

    public HomeController(GoogleContactsService googleContactsService) {
        this.googleContactsService = googleContactsService;
    }

    // ✅ Display Contacts List
    @GetMapping("/contacts")
    public String listContacts(Model model, OAuth2AuthenticationToken authentication) {
        List<Map<String, Object>> connections = googleContactsService.getAllContacts(authentication);
        List<Map<String, String>> contacts = new ArrayList<>();

        for (Map<String, Object> person : connections) {
            String resourceName = person.get("resourceName").toString();
            if (!resourceName.startsWith("people/c")) continue; // Skip Google Profiles

            Map<String, String> contact = new HashMap<>();
            contact.put("id", resourceName);
            contact.put("name", ((List<Map<String, String>>) person.get("names")).get(0).get("displayName"));
            contact.put("email", person.containsKey("emailAddresses") ? ((List<Map<String, String>>) person.get("emailAddresses")).get(0).get("value") : "N/A");
            contact.put("phone", person.containsKey("phoneNumbers") ? ((List<Map<String, String>>) person.get("phoneNumbers")).get(0).get("value") : "N/A");
            contacts.add(contact);
        }

        model.addAttribute("contacts", contacts);
        return "contacts";
    }

    // ✅ Show "Add Contact" Form
    @GetMapping("/contacts/add")
    public String showAddContactForm() {
        return "add-contact";
    }

    // ✅ Handle Contact Creation
    @PostMapping("/contacts/create")
    public String addContact(OAuth2AuthenticationToken authentication,
                             @RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String phone) {
        System.out.println("Add Contact Accessed");
        googleContactsService.addContact(authentication, firstName, lastName, email, phone);
        return "redirect:/contacts";
    }

    // ✅ Handle Contact Deletion
    @PostMapping("/contacts/delete")
    public String deleteContact(OAuth2AuthenticationToken authentication, @RequestParam String resourceName) {
        googleContactsService.deleteContact(authentication, resourceName);
        return "redirect:/contacts";
    }

    // ✅ Load "Edit Contact" Form
    @PostMapping("/contacts/edit")
    public String loadEditForm(@RequestParam String id, Model model, OAuth2AuthenticationToken authentication) {
        System.out.println("📌 Editing Contact ID: " + id);
        Map<String, Object> contactData = googleContactsService.getContactById(authentication, id);
        if (contactData == null) return "redirect:/contacts";

        // Extract details
        String firstName = contactData.containsKey("names")
                ? ((Map<String, String>) ((List<?>) contactData.get("names")).get(0)).get("givenName")
                : "";
        String lastName = contactData.containsKey("names")
                ? ((Map<String, String>) ((List<?>) contactData.get("names")).get(0)).getOrDefault("familyName", "")
                : "";
        String email = contactData.containsKey("emailAddresses")
                ? ((Map<String, String>) ((List<?>) contactData.get("emailAddresses")).get(0)).get("value")
                : "";
        String phone = contactData.containsKey("phoneNumbers")
                ? ((Map<String, String>) ((List<?>) contactData.get("phoneNumbers")).get(0)).get("value")
                : "";

        // Store in Model
        model.addAttribute("contact", Map.of(
                "id", id,
                "firstName", firstName,
                "lastName", lastName,
                "email", email,
                "phone", phone
        ));

        return "edit-contact";
    }

    @PostMapping("/contacts/update")
    public String updateContact(OAuth2AuthenticationToken authentication,
                                @RequestParam String id,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String phone) {
        System.out.println("🔄 Updating Contact ID: " + id);
        googleContactsService.updateContact(authentication, id, firstName, lastName, email, phone);
        return "redirect:/contacts";
    }
}
