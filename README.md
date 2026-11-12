# WeatherWear — Weather & Travel Clothing App

Aplicație Android (Kotlin) care îți arată vremea și îți spune **ce să porți azi**, iar pentru o călătorie îți face **lista de bagaj** în funcție de prognoza de la destinație. Sugestiile vin de la **Google Gemini**. Dacă AI-ul nu răspunde (nu ai cheie, nu ai internet sau s-a terminat cota gratuită), aplicația folosește propriile reguli, așa că întotdeauna primești un răspuns.

- **Vreme:** [Open-Meteo](https://open-meteo.com), gratuit și fără cheie API (prognoză pe 16 zile + căutare de orașe).
- **Sugestii AI:** Gemini REST API, cu cheia gratuită din Google AI Studio.
- **Conturi și date în cloud:** Firebase Authentication + Cloud Firestore.

## Cum acoperă baremul

| Cerință | Unde e în aplicație |
|---|---|
| **Autentificare + validare** (10p) | `LoginActivity`: email și parolă (login, cont nou, resetare parolă). Validarea se face în `Validators.kt`: email valid, parolă de minim 6 caractere cu litere și cifre, confirmarea parolei. Erorile Firebase sunt traduse în mesaje clare în `ErrorMessages.kt`. |
| **Evenimente UI** (10p) | **Click:** pe o zi din prognoză (detalii + ținută), pe o călătorie, pe un articol din bagaj (îl bifează). **Long click:** pe o călătorie (meniu contextual: deschide / distribuie / șterge), pe un articol (editează / șterge), pe cardul de azi (distribuie ținuta). Plus swipe-to-refresh, Snackbar cu „Anulează" la ștergere și **notificarea zilnică** de dimineață (WorkManager). |
| **≥ 2 activități** (10p) | 6 activități: `LoginActivity`, `MainActivity`, `TripsActivity`, `NewTripActivity`, `TripDetailActivity`, `SettingsActivity`. |
| **Meniu funcțional** (10p) | Meniul din toolbar `menu_main.xml`: căutare oraș (SearchView), locația mea, călătoriile mele, setări, despre, deconectare. Meniuri contextuale: `menu_trip_context.xml`, `menu_packing_item.xml`. Meniul călătoriei: `menu_trip_detail.xml`. |
| **Stocare date** (cloud, 20p) | **Cloud Firestore**: călătoriile și listele de bagaj, la `users/{uid}/trips/{id}`, sincronizate live și disponibile și offline. **Local**: setările (°C/°F, notificări, ultimul oraș) în SharedPreferences. |
| **Aspect vizual** (10p) | Material 3 cu temă light/dark, carduri, chips, progress bars, iconițe vectoriale și pentru vreme emoji (fără imagini externe). |
| **UX** (10p) | Erori afișate sub câmpuri, mesaje clare pentru lipsa internetului sau a locației, stări goale cu acțiune, încărcare vizibilă, confirmare înainte de ștergere, „Anulează" după ștergere. |
| **Cod curat** (10p) | Kotlin, comentarii pe fiecare clasă, fără valori hardcodate în UI (toate textele sunt în `strings.xml`). |
| **Modularitate** (10p) | MVVM: Activity → ViewModel → Repository → API/Firebase. Logica (`domain/`) nu depinde de Android și are 22 de teste unitare. |
| **Bonus: creativitate** (5p) | **Login cu Google** (Credential Manager) + sugestii generate de **Gemini AI**. |
| **Bonus: senzori** (5p) | **Accelerometru**: scuturi telefonul și prognoza se actualizează (`ShakeDetector.kt`). **Locație**: vremea de unde ești (`LocationProvider.kt`). |

## Configurare (o singură dată, aproximativ 10 minute)

### 1. Firebase (login + cloud)
1. Intră pe [console.firebase.google.com](https://console.firebase.google.com), apasă **Add project** și creează proiectul (Google Analytics nu e necesar).
2. **Add app → Android**, cu package name **`ro.weatherwear.app`**.
3. Adaugă amprenta **SHA-1**. E necesară pentru login-ul cu Google. O obții rulând în folderul proiectului:
   ```bash
   gradlew signingReport
   ```
   Copiezi valoarea `SHA1` de la varianta `debug`.
4. Descarcă **`google-services.json`** și pune-l în folderul **`app/`**.
5. **Authentication → Sign-in method**: activează **Email/Password** și **Google**.
6. **Firestore Database → Create database**: alege o locație din Europa (de exemplu `eur3`) și pornește în *production mode*. Apoi pune aceste reguli în tab-ul **Rules**, ca fiecare utilizator să-și vadă doar datele lui:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId}/{document=**} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```

> Dacă adaugi SHA-1 după ce ai descărcat `google-services.json`, descarcă fișierul din nou.

### 2. Cheia Gemini (gratuită)
1. Ia o cheie de la [aistudio.google.com/apikey](https://aistudio.google.com/apikey).
2. Scrie-o în **`local.properties`**, în rădăcina proiectului. Fișierul **nu** se urcă pe Git:
   ```
   GEMINI_API_KEY=cheia_ta
   GEMINI_MODEL=gemini-2.5-flash
   ```
   Dacă modelul nu mai e disponibil în free tier, schimbă `GEMINI_MODEL` cu unul din lista din AI Studio.

Fără cheie, aplicația funcționează în continuare, doar că sugestiile vin din reguli (în Setări scrie „Sugestii AI: oprite").

> **Despre securitate:** cheia ajunge în APK, deci cine are aplicația o poate extrage. Pentru un proiect de laborator e acceptabil, dar nu publica APK-ul cu cheia ta. Într-o aplicație reală, apelul către Gemini s-ar face printr-un server (de exemplu Firebase AI Logic sau Cloud Functions).

### 3. Rulare
Deschide folderul în **Android Studio**, așteaptă Gradle Sync și apasă **Run**. Merge pe telefon sau în emulator, cu Android 10 sau mai nou. În emulator, locația o setezi din **Extended controls → Location**.

Din linia de comandă:
```bash
gradlew assembleDebug
```
```bash
gradlew testDebugUnitTest
```

## Structura proiectului

```
app/src/main/java/ro/weatherwear/app/
├── WeatherWearApp.kt          # Application: creează AppContainer și canalul de notificări
├── di/AppContainer.kt         # Dependency injection manual (un singur loc unde se creează totul)
├── data/
│   ├── model/                 # City, Forecast, Trip, PackingItem, sugestii
│   ├── remote/                # Retrofit: Open-Meteo, Gemini; GeminiParser (JSON → modele)
│   └── repository/            # Weather, Suggestion (AI + rezervă), Auth, Trip (Firestore), Settings
├── domain/                    # Logică pură, testată: ClothingAdvisor, Validators, PromptBuilder
├── location/                  # LocationProvider (locație + numele orașului)
├── sensors/                   # ShakeDetector (accelerometru)
├── notifications/             # Notificarea zilnică (WorkManager)
└── ui/
    ├── auth/                  # LoginActivity + LoginViewModel
    ├── main/                  # MainActivity + MainViewModel
    ├── trips/                 # Listă, călătorie nouă, detalii + adaptere
    ├── settings/              # SettingsActivity
    └── common/                # Formatare vreme/călătorii, erori, extensii
```

## Fluxul aplicației

1. **Login**: cu email și parolă sau cu Google. Dacă ești deja conectat, intri direct.
2. **Ecranul principal**: vremea de unde ești (sau din orașul căutat), cardul „Ce să porți azi" de la Gemini și prognoza pe 7 zile.
3. **Planifică o călătorie**: destinație, perioadă, tip (vacanță / city break / munte / afaceri), apoi „Generează lista". Gemini face lista de bagaj pe baza prognozei de acolo, iar tu o salvezi în cloud.
4. **Călătoriile mele**: lista călătoriilor; long click deschide opțiunile.
5. **Detalii călătorie**: prognoza pe zilele călătoriei și checklist-ul de bagaj, pe care îl bifezi. Se salvează imediat în Firestore.
6. **Setări**: °C/°F și notificarea de dimineață cu ținuta zilei.
