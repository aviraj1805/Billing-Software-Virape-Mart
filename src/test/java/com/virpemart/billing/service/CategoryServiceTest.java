package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.virpemart.billing.model.Category;

class CategoryServiceTest {

    @TempDir
    Path temp;

    private TestFixture fixture;
    private CategoryService categories;

    @BeforeEach
    void setUp() {
        fixture = new TestFixture(temp);
        categories = fixture.services.categories();
    }

    @Test
    void startsWithTheDefaultGroceryCategories() {
        List<String> names = categories.list(false).stream().map(Category::name).toList();

        assertEquals(19, names.size());
        assertTrue(names.contains("Dal & Pulses"));
        assertTrue(names.contains("Other"));
    }

    @Test
    void ownerCanAddCategory() {
        Category created = categories.create("  Frozen   Foods ");

        assertEquals("Frozen Foods", created.name(), "extra spaces are cleaned up");
        assertEquals(1, fixture.count("SELECT COUNT(*) FROM audit_log WHERE action = 'CATEGORY_CREATED'"));
    }

    @Test
    void duplicateNameIsRejectedIgnoringCase() {
        ValidationException error = assertThrows(ValidationException.class, () -> categories.create("dal & pulses"));

        assertEquals("name", error.field());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(ValidationException.class, () -> categories.create("   "));
    }

    @Test
    void renameAndSwitchOff() {
        Category other = categories.list(false).stream().filter(c -> c.name().equals("Other")).findFirst().orElseThrow();

        categories.rename(other.id(), "Miscellaneous");
        categories.setActive(other.id(), false);

        assertFalse(categories.list(false).stream().anyMatch(c -> c.id() == other.id()), "hidden when switched off");
        assertTrue(categories.list(true).stream().anyMatch(c -> c.name().equals("Miscellaneous") && !c.active()));
    }

    @Test
    void staffCanReadButNotChange() {
        fixture.signInStaff();

        assertFalse(categories.list(false).isEmpty());
        assertThrows(PermissionDeniedException.class, () -> categories.create("Anything"));
    }
}
