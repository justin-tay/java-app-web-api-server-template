package com.example.commons.accounts.domain;

/**
 * Where an account is in its life: active, or suspended and so unable to sign in (see
 * docs/adr/0031). A removed account no longer exists.
 */
public enum AccountStatus {

	ACTIVE, SUSPENDED

}
