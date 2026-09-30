package com.virpemart.billing.model;

import java.util.List;

/**
 * The shop's details printed on every bill. The owner types them in Settings.
 *
 * @param name         shop name, the big first line of the bill (required)
 * @param secondLine   optional line under the name, for example the shop name in Marathi
 * @param addressLines address, one entry per printed line (may be empty)
 * @param phone        phone number text, or null
 * @param footerLines  closing lines at the bottom of the bill, for example "Thank you, visit again" (may be empty)
 */
public record ShopDetails(String name, String secondLine, List<String> addressLines, String phone,
                          List<String> footerLines) {

    public ShopDetails {
        addressLines = List.copyOf(addressLines);
        footerLines = List.copyOf(footerLines);
    }
}
