package com.caremate.app.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AuthRepository {

    private final FirebaseAuth firebaseAuth;

    public AuthRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public Task<AuthResult> signInAnonymouslyIfNeeded() {
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser != null) {
            return null;
        }

        return firebaseAuth.signInAnonymously();
    }
}
