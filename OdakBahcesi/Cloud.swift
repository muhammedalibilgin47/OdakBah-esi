import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import GoogleSignIn
import UIKit

struct CloudError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Firebase hesap + bulut işlemleri. Veri biçimi Android sürümüyle aynıdır (users/{uid}).
enum Cloud {
    static func configure() {
        if FirebaseApp.app() == nil { FirebaseApp.configure() }
    }

    static var user: User? { Auth.auth().currentUser }

    @MainActor
    static func signInWithGoogle() async throws -> User {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            throw CloudError(message: "Firebase ayarı bulunamadı.")
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let root = UIApplication.shared.connectedScenes
            .compactMap({ ($0 as? UIWindowScene)?.keyWindow?.rootViewController }).first else {
            throw CloudError(message: "Ekran bulunamadı.")
        }
        var top = root
        while let presented = top.presentedViewController { top = presented }

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: top)
        guard let idToken = result.user.idToken?.tokenString else {
            throw CloudError(message: "Google kimliği alınamadı.")
        }
        let credential = GoogleAuthProvider.credential(withIDToken: idToken,
                                                       accessToken: result.user.accessToken.tokenString)
        return try await Auth.auth().signIn(with: credential).user
    }

    static func signInWithEmail(_ email: String, password: String, create: Bool) async throws -> User {
        let e = email.trimmingCharacters(in: .whitespacesAndNewlines)
        if create {
            return try await Auth.auth().createUser(withEmail: e, password: password).user
        } else {
            return try await Auth.auth().signIn(withEmail: e, password: password).user
        }
    }

    static func resetPassword(_ email: String) async throws {
        try await Auth.auth().sendPasswordReset(withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines))
    }

    static func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }

    static func upload(uid: String, data: [String: Any]) async throws {
        try await Firestore.firestore().collection("users").document(uid).setData(data, merge: true)
    }

    static func download(uid: String) async throws -> [String: Any]? {
        try await Firestore.firestore().collection("users").document(uid).getDocument().data()
    }

    /// Arkadaşların güncel özetleri (10'arlı gruplar halinde)
    static func fetchUsers(_ ids: [String]) async throws -> [String: [String: Any]] {
        var out: [String: [String: Any]] = [:]
        let unique = Array(Set(ids))
        var i = 0
        while i < unique.count {
            let chunk = Array(unique[i..<min(i + 10, unique.count)])
            let snap = try await Firestore.firestore().collection("users")
                .whereField(FieldPath.documentID(), in: chunk).getDocuments()
            for d in snap.documents { out[d.documentID] = d.data() }
            i += 10
        }
        return out
    }

    static func handle(_ url: URL) {
        _ = GIDSignIn.sharedInstance.handle(url)
    }

    /// Hata mesajını Türkçeleştirir
    static func message(_ error: Error) -> String {
        let ns = error as NSError
        if ns.domain == AuthErrorDomain, let code = AuthErrorCode(rawValue: ns.code) {
            switch code {
            case .weakPassword: return "Şifre en az 6 karakter olmalı."
            case .emailAlreadyInUse: return "Bu e-posta zaten kayıtlı. \"Giriş yap\"ı dene."
            case .userNotFound: return "Bu e-postayla kayıtlı hesap yok. \"Kayıt ol\"u dene."
            case .wrongPassword, .invalidCredential: return "E-posta veya şifre hatalı."
            case .invalidEmail: return "E-posta adresi geçersiz."
            case .networkError: return "İnternet bağlantısı yok."
            default: break
            }
        }
        return error.localizedDescription
    }

    static func isCancel(_ error: Error) -> Bool {
        let ns = error as NSError
        return ns.domain == kGIDSignInErrorDomain && ns.code == GIDSignInError.canceled.rawValue
    }

    static func num(_ x: Any?) -> Double { (x as? NSNumber)?.doubleValue ?? 0 }
}
