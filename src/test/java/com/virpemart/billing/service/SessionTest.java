package com.virpemart.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.virpemart.billing.model.Role;
import com.virpemart.billing.model.User;

class SessionTest {

    private static final User OWNER = new User(1, "owner", "Owner", Role.OWNER, true);
    private static final User STAFF = new User(2, "helper", "Helper", Role.STAFF, true);

    @Test
    void nobodySignedInIsRefused() {
        Session session = new Session();

        assertTrue(session.currentUser().isEmpty());
        assertThrows(PermissionDeniedException.class, session::requireSignedIn);
        assertThrows(PermissionDeniedException.class, session::requireOwner);
    }

    @Test
    void ownerCanDoOwnerActions() {
        Session session = new Session();
        session.signIn(OWNER);

        assertEquals(OWNER, session.requireOwner());
        assertEquals(OWNER, session.requireSignedIn());
    }

    @Test
    void staffCannotDoOwnerActions() {
        Session session = new Session();
        session.signIn(STAFF);

        assertEquals(STAFF, session.requireSignedIn());
        PermissionDeniedException error = assertThrows(PermissionDeniedException.class, session::requireOwner);
        assertTrue(error.getMessage().contains("Only the owner"));
    }

    @Test
    void switchedOffUserCannotSignIn() {
        Session session = new Session();

        assertThrows(PermissionDeniedException.class,
                () -> session.signIn(new User(3, "old", "Old", Role.STAFF, false)));
        assertTrue(session.currentUser().isEmpty());
    }

    @Test
    void signOutClearsTheUser() {
        Session session = new Session();
        session.signIn(OWNER);
        session.signOut();

        assertTrue(session.currentUser().isEmpty());
    }
}
