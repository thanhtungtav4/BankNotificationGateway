package com.example.bankgateway.domain.model

data class AllowedPackage(
    val packageName: String,
    val appName: String?,
    val bankName: String?,
    val isActive: Boolean = true
)
