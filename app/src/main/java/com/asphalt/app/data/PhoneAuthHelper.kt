package com.asphalt.app.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Binds a verified mobile number (SMS OTP) to the signed-in Google account.
 * Firebase Auth guarantees that one phone credential can be linked to ONE account only:
 * a second Google account trying to use the same number gets FirebaseAuthUserCollisionException.
 * That is the "dual binding" (Google e-mail  <->  phone) enforced by Google's servers, and the
 * Firestore rules additionally require the phone_number claim in the ID token.
 */
object PhoneAuthHelper {

    sealed class Event {
        data class CodeSent(val verificationId: String) : Event()
        data class AutoVerified(val credential: PhoneAuthCredential) : Event()
        data class Error(val messageArabic: String) : Event()
    }

    fun sendCode(activity: Activity, e164Phone: String, onEvent: (Event) -> Unit) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = onEvent(Event.AutoVerified(credential))
            override fun onVerificationFailed(e: FirebaseException) = onEvent(Event.Error(describe(e)))
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) =
                onEvent(Event.CodeSent(verificationId))
        }
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(e164Phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** Returns null on success or an Arabic error message. */
    suspend fun link(credential: PhoneAuthCredential): String? {
        val user = Firebase.auth.currentUser ?: return "انتهت الجلسة، سجّل الدخول مرة أخرى"
        return try {
            user.linkWithCredential(credential).await()
            user.getIdToken(true).await()   // refresh so the token carries the phone_number claim
            null
        } catch (e: FirebaseAuthUserCollisionException) {
            "هذا الرقم مرتبط بحساب Google آخر ولا يمكن استخدامه مع هذا الحساب"
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            "رمز التحقق غير صحيح أو منتهي"
        } catch (e: Exception) {
            describe(e)
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "رقم الهاتف غير صالح"
        is FirebaseTooManyRequestsException -> "محاولات كثيرة، حاول لاحقاً"
        is FirebaseNetworkException -> "تحقق من اتصال الإنترنت"
        else -> e.localizedMessage ?: "فشل التحقق من رقم الهاتف"
    }
}
