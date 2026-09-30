package com.virpemart.billing.service;

import java.util.Optional;

import com.virpemart.billing.model.User;

/**
 * Who is signed in right now. Services call {@link #requireSignedIn()} or {@link #requireOwner()}
 * before doing anything, so permissions are enforced even if a button is shown by mistake.
 */
public final class Session {

    private volatile User currentUser;

    public Optional<User> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public void signIn(User user) {
        if (!user.active()) {
            throw new PermissionDeniedException("This user account is switched off. Please ask the owner.");
        }
        this.currentUser = user;
    }

    public void signOut() {
        this.currentUser = null;
    }

    /** Returns the signed-in user, or stops the action if nobody is signed in. */
    public User requireSignedIn() {
        User user = currentUser;
        if (user == null) {
            throw new PermissionDeniedException("Please sign in first.");
        }
        return user;
    }

    /** Returns the signed-in owner, or stops the action if the user is not the owner. */
    public User requireOwner() {
        User user = requireSignedIn();
        if (!user.isOwner()) {
            throw new PermissionDeniedException("Only the owner can do this. Please ask the owner.");
        }
        return user;
    }
}
