package com.example.miarriendo_v1;

import android.content.Context;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

/** Traduce los errores de Firebase Auth a mensajes para el usuario. */
final class AuthErrors {

    private AuthErrors() {
    }

    static String mensaje(Context context, Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) {
            return context.getString(R.string.auth_error_credentials);
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return context.getString(R.string.auth_error_email_in_use);
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return context.getString(R.string.auth_error_weak_password);
        }
        if (e instanceof FirebaseNetworkException) {
            return context.getString(R.string.auth_error_network);
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return context.getString(R.string.auth_error_too_many);
        }
        return context.getString(R.string.auth_error_generic, e == null ? "" : e.getMessage());
    }
}
