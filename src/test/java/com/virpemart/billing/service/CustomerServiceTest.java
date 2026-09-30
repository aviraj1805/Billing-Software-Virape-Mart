package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.virpemart.billing.model.CustomerSummary;
import com.virpemart.billing.model.Money;
import com.virpemart.billing.repository.CustomerRepository.DuesTotals;

class CustomerServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private CustomerService customers;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        customers = fixture.services.customers();
    }

    private static CustomerInput input(String name, String phone, String oldDues) {
        return new CustomerInput(name, phone, null, null, oldDues);
    }

    // ------------------------------------------------------------------ adding

    @Test
    void addsCustomersWithAutomaticNumbers() {
        CustomerSummary ramesh = customers.create(new CustomerInput("  Ramesh   Patil ", "98765 43210",
                " Shivaji Nagar ", "Pays on the 1st", ""));
        CustomerSummary sunita = customers.create(input("Sunita Jadhav", null, null));

        assertEquals("C0001", ramesh.customer().customerNo());
        assertEquals("C0002", sunita.customer().customerNo());
        assertEquals("Ramesh Patil", ramesh.customer().name());
        assertEquals("9876543210", ramesh.customer().phone());
        assertEquals("Shivaji Nagar", ramesh.customer().address());
        assertEquals("Pays on the 1st", ramesh.customer().notes());
        assertNull(sunita.customer().phone());
        assertEquals(Money.ZERO, ramesh.balance());
        assertEquals(2, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'CUSTOMER_CREATED'"));
    }

    @Test
    void oldDuesFromThePaperKhataBecomeTheOpeningEntry() {
        CustomerSummary ramesh = customers.create(input("Ramesh", null, "1,250.50"));

        assertEquals(Money.ofPaise(125050), ramesh.balance());
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM customer_ledger WHERE entry_type = 'OPENING'"
                + " AND amount_paise = 125050 AND note = 'Old dues from paper khata'"));
    }

    @Test
    void zeroOrBlankOldDuesAddNoEntry() {
        customers.create(input("Ramesh", null, "0"));
        customers.create(input("Suresh", null, "  "));

        assertEquals(0, fixture.count("SELECT COUNT(*) FROM customer_ledger"));
    }

    @Test
    void badOldDuesAreRejectedAndNothingIsSaved() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> customers.create(input("Ramesh", null, "abc")));

        assertEquals("oldDues", error.field());
        assertEquals(0, fixture.count("SELECT COUNT(*) FROM customers"));
    }

    @Test
    void nameIsRequired() {
        ValidationException error = assertThrows(ValidationException.class, () -> customers.create(input(" ", null, null)));

        assertEquals("name", error.field());
    }

    // ------------------------------------------------------------------ phone numbers

    @ParameterizedTest
    @CsvSource({
            "9876543210, 9876543210",
            "98765 43210, 9876543210",
            "+91 98765 43210, 9876543210",
            "+91-98765-43210, 9876543210",
            "919876543210, 9876543210",
            "09876543210, 9876543210",
            "0233-2345678, 2332345678",
            "(0233) 234 5678, 2332345678"
    })
    void phoneNumbersAreCleanedToTenDigits(String typed, String stored) {
        assertEquals(stored, CustomerService.normalizePhone(typed));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "98765432101", "phone", "9876543210x", "+1 9876543210"})
    void badPhoneNumbersAreRejected(String typed) {
        ValidationException error = assertThrows(ValidationException.class, () -> CustomerService.normalizePhone(typed));

        assertEquals("phone", error.field());
    }

    @Test
    void twoCustomersCannotShareAPhone() {
        customers.create(input("Ramesh", "9876543210", null));

        ValidationException error = assertThrows(ValidationException.class,
                () -> customers.create(input("Suresh", "+91 98765 43210", null)));

        assertTrue(error.getMessage().contains("C0001 Ramesh"), error.getMessage());
    }

    @Test
    void sameNameIsAllowedBecausePeopleShareNames() {
        customers.create(input("Ramesh", null, null));

        assertEquals("C0002", customers.create(input("Ramesh", null, null)).customer().customerNo());
    }

    // ------------------------------------------------------------------ editing

    @Test
    void editChangesDetailsButNeverTheKhata() {
        CustomerSummary ramesh = customers.create(input("Ramesh", null, "500"));

        CustomerSummary edited = customers.update(ramesh.customer().id(),
                new CustomerInput("Ramesh Patil", "9876543210", "Ward 5", null, "9999"));

        assertEquals("Ramesh Patil", edited.customer().name());
        assertEquals("C0001", edited.customer().customerNo(), "number never changes");
        assertEquals(Money.ofRupees(500), edited.balance(), "old dues field is ignored when editing");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'CUSTOMER_UPDATED'"));
    }

    @Test
    void editCannotTakeAnotherCustomersPhone() {
        customers.create(input("Ramesh", "9876543210", null));
        CustomerSummary suresh = customers.create(input("Suresh", null, null));

        assertThrows(ValidationException.class,
                () -> customers.update(suresh.customer().id(), input("Suresh", "9876543210", null)));
    }

    // ------------------------------------------------------------------ searching and totals

    @Test
    void searchFindsByNamePhoneOrNumber() {
        customers.create(input("Ramesh Patil", "9876543210", null));
        customers.create(input("Suresh Patil", "9123456780", null));
        customers.create(input("Anita More", null, null));

        assertEquals(2, customers.search("patil", false, false, 50).size());
        assertEquals("Ramesh Patil", customers.search("98765", false, false, 50).getFirst().customer().name());
        assertEquals("Anita More", customers.search("c0003", false, false, 50).getFirst().customer().name());
        assertEquals(1, customers.search("sur pat", false, false, 50).size());
        assertEquals(3, customers.search("", false, false, 50).size());
    }

    @Test
    void canListOnlyCustomersWithDues() {
        customers.create(input("Ramesh", null, "500"));
        customers.create(input("Suresh", null, null));

        List<CustomerSummary> withDues = customers.search("", false, true, 50);

        assertEquals(1, withDues.size());
        assertTrue(withDues.getFirst().hasDues());
    }

    @Test
    void duesTotalsCountOnlyCustomersWhoOwe() {
        customers.create(input("Ramesh", null, "500"));
        customers.create(input("Suresh", null, "250.50"));
        CustomerSummary anita = customers.create(input("Anita", null, null));
        fixture.services.ledger().receivePayment(anita.customer().id(), "100", com.virpemart.billing.model.PaymentMode.CASH, null);

        DuesTotals totals = customers.duesTotals();

        assertEquals(2, totals.customersWithDues());
        assertEquals(Money.ofPaise(75050), totals.totalDues(), "Anita's advance is not subtracted");
    }

    // ------------------------------------------------------------------ switching off and permissions

    @Test
    void switchedOffCustomersAreHiddenButKeepTheirKhata() {
        CustomerSummary ramesh = customers.create(input("Ramesh", null, "500"));

        CustomerSummary off = customers.setActive(ramesh.customer().id(), false);

        assertFalse(off.customer().active());
        assertEquals(Money.ofRupees(500), off.balance());
        assertTrue(customers.search("ramesh", false, false, 50).isEmpty());
        assertEquals(1, customers.search("ramesh", true, false, 50).size());
    }

    @Test
    void staffCanAddAndEditButNotSwitchOff() {
        fixture.signInStaff();

        CustomerSummary ramesh = customers.create(input("Ramesh", null, "500"));
        customers.update(ramesh.customer().id(), input("Ramesh Patil", null, null));

        assertThrows(PermissionDeniedException.class, () -> customers.setActive(ramesh.customer().id(), false));
    }
}
