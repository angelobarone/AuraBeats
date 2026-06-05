# AuraBeats 🎧📈

**Sincronizzatore ed adattatore musicale basato sulla biometria dello smartwatch ed Health Connect.**

AuraBeats è un'applicazione Android sviluppata in **Kotlin** con **Jetpack Compose** e **Material Design 3**. L'applicazione sincronizza l'esperienza musicale dell'utente e il ritmo degli allenamenti in tempo reale, sfruttando i dati biometrici provenienti da wearable (tramite **Health Connect** o simulatori integrati) e comunicando con un server **FastAPI** esterno per la generazione personalizzata dei brani.

---

## ✨ Funzionalità principali

1. **Adattamento Musicale Real-Time**: Sincronizzazione del tempo e del contenuto audio in base alla frequenza cardiaca (HR) e all'intensità dell'allenamento.
2. **Supporto Doppia Traccia (Weightlifting ZIP)**: Per l'attività di sollevamento pesi (*weightlifting*), l'app riceve un pacchetto ZIP contenente due tracce distinte. L'audioplayer esegue una dissolvenza incrociata (**crossfade**) in tempo reale tra le due tracce in base all'attuale battito cardiaco (soglia di transizione a **115 BPM**).
3. **Gestione Intelligente della Cache & Download Unico**: Quando viene richiesto un aggiornamento biometrico dall'applicazione, viene eseguito un singolo controllo di stato per verificare se una nuova traccia è pronta, evitando cicli continui di polling. Se il file è già stato scaricato (verificato tramite nome del file in memoria), il download viene saltato per risparmiare banda ed energia.
4. **Sintetizzatore Intelligente Offline (Fallback)**: In scenari di offline o impossibilità di raggiungere le API di back-end, l'applicazione attiva in automatico un sintetizzatore audio dynamically-generated, garantendo la continuità acustica e del ritmo di allenamento.
5. **Integrazione Room Database**: Archiviazione locale dei dati delle sessioni di allenamento e degli snapshot biometrici storici (frequenza cardiaca, calorie, passi, ecc.) per visualizzare statistiche dettagliate ed analisi dell'andamento.
6. **Health Connect Integration**: Raccolta formale e centralizzata delle metriche cardiache di fitness direttamente dalle API di Google su Android.

---

## 🛠️ Architettura Tecnica

Il progetto segue le linee guida dell'architettura moderna di Android (**MVVM / Clean Architecture**):

### 📁 Struttura della Codebase

* **`com.example.ui`**: Componenti dell'interfaccia utente (Jetpack Compose).
    * `WorkoutApp.kt`: Schermate, layout Material 3, grafici per visualizzare la telemetria dinamica.
    * `WorkoutViewModel.kt`: Gestione dello stato dell'allenamento, timer, accumulo di metriche Health Connect ed invio aggiornamenti asincroni al repository.
* **`com.example.db`**: Strato di persistenza locale (SQLite/Room).
    * `Entities.kt`: Tabelle `SessionEntity` e `BiometricSnapshotEntity`.
    * `SessionDao.kt`: Query per la memorizzazione e il recupero delle sessioni storiche e snapshot temporali.
    * `WorkoutRepository.kt`: Logica di sincronizzazione tra il database locale Room e i servizi di rete FastAPI. Unzipping asincrono e gestione della cache dei download.
* **`com.example.audio`**: Gestione del playback audio analitico.
    * `WorkoutAudioPlayer.kt`: Controllo di `MediaPlayer`, regolazione in tempo reale della velocità di riproduzione (`PlaybackParams.speed`) in base alla frequenza cardiaca e dissolvenze incrociate asincrone tra file dual-track per il weightlifting.
* **`com.example.network`**: Strato di rete.
    * `FastApiService.kt`: Definizione delle interfacce Retrofit e impostazione dinamica dei nodi endpoint (ngrok/local).
* **`com.example.health`**: Manager per la comunicazione e autorizzazioni di **Health Connect**.

---

## 🔄 Flusso di Integrazione Server (FastAPI)

L'applicazione si interfaccia con le seguenti rotte API per gestire il ciclo vitale dell'audio:

1. **Creazione Sessione**: `POST /session/start`
    * Avvia una sessione con l'attività selezionata. L'applicazione avvia il polling temporaneo finché la traccia iniziale (WAV o ZIP contenente le doppie tracce) è pronta, dopodiché la scarica ed avvia la riproduzione.
2. **Aggiornamento Biometrico & Nuovi Chunk**: `POST /session/{session_id}/update`
    * Invia gli ultimi parametri del battito cardiaco medio, passi e preferenze dell'utente.
3. **Controllo Stato (No-polling continuo)**: `GET /session/{session_id}/status`
    * Viene chiamato esattamente **una volta** per aggiornamento biometrico richiesto dal ViewModel.
    * Restituisce le informazioni sull'ultimo audio generato (`audio_url`, `audio_filename`, `audio_type`).
    * Se `audio_filename` è già presente in memoria, l'app evita un download ridondante.
4. **Download Audio**: `GET /session/{session_id}/audio`
    * Scarica il file audio di tipo WAV o ZIP (in base alla disponibilità o dinamicità dell'attività).

---

## 🚀 Requisiti e Setup

### Prerequisiti
* Android SDK 34 (`compileSdk = 34`, `targetSdk = 34`)
* Gradle con supporto a Kotlin DSL (.gradle.kts)
* Server FastAPI di supporto attivo sull'endpoint configurato in `NetworkClient` (es. tramite ngrok) o configurazione offline automatica.

### Setup API Key
Per abilitare funzionalità intelligenti avanzate derivanti da servizi cloud o Gemini API, inserire la chiave API segreta nell'interfaccia apposita di AI Studio (l'applicazione leggerà la chiave asincronamente tramite `BuildConfig`).

---

## 🧪 Testing

L'applicazione include:
* **Unit Tests**: Test logici su ViewModel e repository.
* **Robolectric & Roborazzi**: Test sui flussi grafici con cattura di screenshot per verificare la fedeltà del layout ed evitare regressioni visive.

È possibile eseguire i test locali utilizzando il comando:
```bash
gradle :app:testDebugUnitTest
```