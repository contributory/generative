package org.starfall.multigateway.shared.model.auth

/** Runtime-only state; never infer missing credentials from an empty token in a metadata record. */
enum class CredentialLoadState { NOT_CONFIGURED, NOT_REQUESTED, LOADING, LOADED, ERROR }
