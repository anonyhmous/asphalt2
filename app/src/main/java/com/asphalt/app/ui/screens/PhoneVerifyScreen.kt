package com.asphalt.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asphalt.app.data.PhoneAuthHelper
import com.asphalt.app.data.PhoneNumbers
import com.asphalt.app.ui.theme.AppPrimary
import kotlinx.coroutines.launch

/**
 * Step 2 of the dual binding: after Google sign-in the driver must verify his mobile number by SMS.
 * The number is then linked to the Google account by Firebase Auth (one number = one account).
 */
@Composable
fun PhoneVerifyScreen(onVerified: () -> Unit, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun linkCredential(credential: com.google.firebase.auth.PhoneAuthCredential) {
        scope.launch {
            busy = true
            val err = PhoneAuthHelper.link(credential)
            busy = false
            if (err == null) onVerified() else error = err
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("توثيق رقم الهاتف", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text(
            "لحماية الفترة التجريبية والاشتراك، يُربط رقمك بحساب Google الخاص بك مرة واحدة. لا يمكن استخدام نفس الرقم مع حساب آخر.",
            fontSize = 13.sp, color = Color(0xFFCBD5E1), textAlign = TextAlign.Center, lineHeight = 19.sp
        )
        Spacer(Modifier.height(20.dp))

        if (verificationId == null) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.take(16) },
                label = { Text("رقم الموبايل (مثال 01012345678)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().testTag("phone_input")
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy && activity != null,
                onClick = {
                    val e164 = PhoneNumbers.toE164(phone)
                    if (e164 == null) { error = "اكتب رقماً مصرياً صحيحاً"; return@Button }
                    error = null; busy = true
                    PhoneAuthHelper.sendCode(activity!!, e164) { ev ->
                        busy = false
                        when (ev) {
                            is PhoneAuthHelper.Event.CodeSent -> verificationId = ev.verificationId
                            is PhoneAuthHelper.Event.AutoVerified -> linkCredential(ev.credential)
                            is PhoneAuthHelper.Event.Error -> error = ev.messageArabic
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("send_code_button")
            ) { Text(if (busy) "جاري الإرسال..." else "إرسال رمز التحقق", fontWeight = FontWeight.Bold) }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("رمز التحقق (6 أرقام)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("otp_input")
            )
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !busy && code.length == 6,
                onClick = {
                    error = null
                    linkCredential(com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId!!, code))
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("verify_code_button")
            ) { Text(if (busy) "جاري التحقق..." else "تأكيد", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { verificationId = null; code = ""; error = null }) { Text("تغيير الرقم") }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Color(0xFFFCA5A5), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onSignOut) { Text("تسجيل الخروج", color = Color(0xFF94A3B8)) }
    }
}
