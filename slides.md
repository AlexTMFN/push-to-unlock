---
theme: 'dracula'
title: 'App Locker'
---

# App Locker

A presentation by:
- VOICU MARIO-CRISTIAN
- TUDOR CONSTANTIN-ALIN
- CHIRCEF DAN-ALEXANDRU
- POSPAI ALEXANDRU
- ZOTA ANDREI

---

# Motivația Alegerii Aplicației

- **1. Combaterea sedentarismului**
  - Utilizatorii petrec ore în șir pe social media (Instagram, TikTok). Prin condiționarea accesului de efectuarea unor flotări, transformi un obicei pasiv într-unul activ.

- **2. Gamificarea disciplinei**
  - Spre deosebire de un App Locker clasic care cere doar un PIN, acesta impune o barieră de efort. Dacă vrei neapărat să intri pe Facebook, trebuie să plătești cu efort fizic.

---

# Aplicații Similare și Studiul Pieței

## Analiza concurenței (Exemple relevante):

- **PushUp Time / PushUpLock:** Aplicații care blochează accesul la social media până când utilizatorul face un set de flotări. Folosesc camera (AI) sau senzorul de proximitate pentru numărare.

- **Fitlock / StepBloc:** Se bazează pe obiective de fitness generale (ex: trebuie să faci 5000 de pași ca să deblochezi YouTube pentru 15 minute).

- **AppDetox / StayFocused:** App lockere clasice care folosesc doar limite de timp sau parole, fără componentă de efort fizic (lipsesc elementul de gamificare a sănătății).

---

# Cerințele și Funcționalitățile Aplicației

- **Selectarea aplicațiilor protejate:** O listă cu toate aplicațiile instalate de unde utilizatorul alege pe care dorește să le blocheze (ex: Instagram, TikTok, Facebook).

- **Interfața de blocare (Overlay):** O fereastră care apare automat peste aplicația restricționată și care afișează numărul de flotări necesar.

- **Contorizare în timp real:** Detectarea automată a flotărilor (fără a atinge ecranul cu mâna).

---

# Prototipul Grafic (Mock-up)

## Ecranul Principal — „Push to Unlock” (Dashboard)

- **Header:** Afișează titlul aplicației și un buton de resetare a progresului.
- **Statistici:** Prezintă clar trei indicatori numerici mari: totalul de flotări efectuate, numărul de aplicații deblocate și numărul de aplicații încă blocate.
- **Lista aplicațiilor blocate:** Pentru fiecare aplicație (Instagram, TikTok, YouTube, etc.) sunt afișate:
  - Numele aplicației.
  - Numărul de flotări rămase necesare (ex: "10 more needed").
  - O bară de progres (0/10).
  - Un buton etichetat “Do Push-ups" care permite utilizatorului să înceapă antrenamentul pentru acea aplicație specifică.

---

# Prototipul Grafic (Mock-up)

## Ecranul de Setări — „App Settings”

- **Descriere:** Un subtitlu explicativ menționează că utilizatorul poate selecta ce aplicații să fie blocate și poate seta câte flotări sunt necesare pentru fiecare.
- **Toggles și Controale:** Pentru fiecare aplicație (Instagram, TikTok, etc.) există:
  - Un **switch (toggle)** pentru a activa/dezactiva blocarea acelei aplicații.
  - Afișarea progresului curent (ex: "0/10 push-ups completed").
  - Un **selector numeric (stepper)** etichetat “Push-ups: 10” care permite utilizatorului să crească sau să scadă numărul de repetări necesare pentru deblocare.

---

# Prototipul Grafic (Mock-up)

## Ecranul de Scanare — „Camera Access Required / Tracker”

- **Stare Eroare:** Afișează un mesaj vizibil "Camera Access Denied", împreună cu instrucțiuni pas cu pas despre cum să activeze permisiunile camerei în browser, în cazul în care accesul nu a fost acordat.
- **Vizualizare Tracker:** În partea centrală, un pătrat mare (cadru video) simulează camera activă, având inscripționat textul "Get into position".
- **Ghidaj Utilizator:** Sub cadrul video, există instrucțiuni text care îl sfătuiesc pe utilizator să se poziționeze astfel încât întregul corp să fie vizibil în cadru, asigurând o detecție corectă a flotărilor. Un buton "Go Back" permite revenirea la ecranul principal.

---

# Marketingul si Monetizarea

## 1. Freemium – Funcționalități de bază gratuite, premium pe abonament

- **Gratuit:**
  - Blocarea a maxim 3 aplicații
  - Setarea unui număr fix de flotări (ex: doar 10 per aplicație)
  - Tracking de bază cu camera

- **Premium (abonament lunar/anual):**
  - Număr nelimitat de aplicații blocate
  - Setare personalizată a numărului de flotări per aplicație
  - Statistici avansate (istoric zilnic/săptămânal, calorii arse, streak-uri)
  - Teme și personalizare interfață
  - Sincronizare între multiple dispozitive
  - Export date de antrenament

---

# Marketingul si Monetizarea

## 2. Achiziții în aplicație (One-time purchases)

### Pachete de funcționalități:
- **Unlock All Apps Pack:** $4.99 – elimină limita de aplicații blocate
- **Custom Goals Pack:** $2.99 – permite setarea numărului personalizat de flotări
- **Statistics Pack:** $1.99 – deblochează statistici avansate și grafice
- **Themes Pack:** $0.99 per temă - teme vizuale premium

**Avantaj:** Utilizatorii plătesc o singură dată, fără angajament lunar.

---

# ADS

| Tip reclamă | Locație | Monetizare |
|---|---|---|
| **Banner ads** | Partea de jos a ecranului principal și a ecranului de setări | CPM scăzut, dar constant |
| **Interstitial ads** | După finalizarea unei sesiuni de flotări (la deblocarea aplicației) | CPM mediu, moment de tranziție natural |
| **Rewarded video ads** | Utilizatorul poate viziona un anunț pentru a reduce numărul de flotări necesar (ex: 30 secunde de reclamă = -3 flotări) | CPM ridicat, utilizatorii aleg activ să vizioneze |
| **Native ads** | În lista de aplicații blocate, ca sugestie de „apps to help you stay focused” | CPM mediu, mai puțin intruzive |

---

# Webliografie

## Studiu de Piață și Concepte (Aplicații Similare)

- **Google Play Store – Health & Fitness Apps:**
  [https://play.google.com/store/apps/category/HEALTH_AND_FITNESS](https://play.google.com/store/apps/category/HEALTH_AND_FITNESS)
