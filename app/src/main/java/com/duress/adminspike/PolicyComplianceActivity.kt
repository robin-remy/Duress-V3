package com.duress.adminspike

import android.app.Activity
import android.os.Bundle

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // El spike no aplica politicas: confirma y termina para cerrar el provisioning.
        setResult(RESULT_OK)
        finish()
    }
}
