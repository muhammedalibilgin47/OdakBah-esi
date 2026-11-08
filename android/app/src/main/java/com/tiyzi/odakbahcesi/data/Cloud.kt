package com.tiyzi.odakbahcesi.data

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tiyzi.odakbahcesi.R
import kotlinx.coroutines.tasks.await

/** Kullanıcı vazgeçtiğinde gösterilmeyecek hata. */
class SignInCancelled : Exception()

/**
 * Firebase hesap + bulut işlemleri.
 * Veri biçimi iPhone sürümüyle aynıdır (users/{uid}).
 */
object Cloud {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val user: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        val cm = CredentialManager.create(activity)
        val option = GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            cm.getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw SignInCancelled()
        } catch (e: NoCredentialException) {
            throw Exception("Telefonda Google hesabı bulunamadı. Ayarlar'dan bir Google hesabı ekleyin.")
        }
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            val firebaseCred = GoogleAuthProvider.getCredential(google.idToken, null)
            return auth.signInWithCredential(firebaseCred).await().user
                ?: throw Exception("Giriş tamamlanamadı")
        }
        throw Exception("Google kimliği alınamadı")
    }

    suspend fun signInWithEmail(email: String, password: String, create: Boolean): FirebaseUser {
        val task = if (create) auth.createUserWithEmailAndPassword(email.trim(), password)
        else auth.signInWithEmailAndPassword(email.trim(), password)
        return task.await().user ?: throw Exception("Giriş tamamlanamadı")
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut(activity: Activity?) {
        auth.signOut()
        if (activity != null) {
            runCatching { CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }

    suspend fun upload(uid: String, data: Map<String, Any?>) {
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun download(uid: String): Map<String, Any?>? =
        db.collection("users").document(uid).get().await().data

    /** Arkadaşların güncel özetleri (10'arlı gruplar halinde). */
    suspend fun fetchUsers(ids: List<String>): Map<String, Map<String, Any?>> {
        val out = mutableMapOf<String, Map<String, Any?>>()
        ids.distinct().chunked(10).forEach { chunk ->
            val snap = db.collection("users").whereIn(FieldPath.documentId(), chunk).get().await()
            snap.documents.forEach { d -> d.data?.let { out[d.id] = it } }
        }
        return out
    }

    /** Hata mesajını Türkçeleştirir. */
    fun message(e: Throwable): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "Şifre en az 6 karakter olmalı."
        is FirebaseAuthUserCollisionException -> "Bu e-posta zaten kayıtlı. \"Giriş yap\"ı dene."
        is FirebaseAuthInvalidUserException -> "Bu e-postayla kayıtlı hesap yok. \"Kayıt ol\"u dene."
        is FirebaseAuthInvalidCredentialsException -> "E-posta veya şifre hatalı."
        else -> e.localizedMessage ?: "Bir hata oluştu."
    }

    // ------------------------------------------------ Ortak veri biçimi yardımcıları

    /** Android bitki adı → ortak anahtar (iPhone ile aynı) */
    fun plantKey(name: String): String = if (name == "AGAC") "mese" else name.lowercase()

    /** Ortak anahtar → Android bitki adı */
    fun plantName(key: String): String = if (key == "mese") "AGAC" else key.uppercase()

    fun num(x: Any?): Double = (x as? Number)?.toDouble() ?: 0.0
}
