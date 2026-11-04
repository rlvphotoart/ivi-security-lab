# Testare Bluetooth IVI din telefon — versiunea 1.1

Instalează numai `ivi-lab-probe.apk` pe telefonul cu Android 14 sau mai nou. Nu trebuie instalată aplicația pe IVI. Modul **IVI**, afișat la pornire, folosește serviciile Bluetooth pe care mașina le expune deja.

Testele automate au trecut pe emulator Android 14/API 34 pentru exact APK-urile semnate livrate, precum și local pe Android 16/API 36. [Rezultatul Android 14](https://github.com/rlvphotoart/ivi-security-lab/actions/runs/41893242295). Au trecut 14 verificări de protocol și 27 pentru servicii/politica de asociere. Conexiunea fizică telefon–IVI rămâne de validat pe vehicul/banc.

## Procedură

1. Înainte de acordarea permisiunilor, poți apăsa **Check permission boundary**. Acesta verifică telefonul și permisiunile aplicației; nu este un verdict despre IVI.
2. Apasă **Grant Nearby devices permissions** și activează Bluetooth prin dialogul sistemului.
3. Pune IVI-ul în modul de asociere/descoperire. Apasă **Discover nearby peers**, apoi **Choose peer**. Dacă telefonul este deja asociat cu mașina, folosește **List bonded peers**.
4. Selectează exact IVI-ul de test. **Request pairing** folosește mecanismul normal al sistemului. Confirmă sau respinge cererea pe dispozitive, conform cazului de test. Poți folosi **Open system Bluetooth settings** pentru asociere, deconectare sau uitarea dispozitivului. Aplicația nu șterge automat asocierile.
5. Apasă **Request IVI service discovery (SDP)**, apoi consultă **Evidence**. Rezultatul poate conține UUID-uri din cache; nu dovedește că toate serviciile sunt disponibile în acel moment. Poți citi explicit cache-ul prin **Read cached service UUIDs**.
6. Folosește **Choose advertised RFCOMM service**. Sunt disponibile numai serviciile cunoscute drept compatibile cu RFCOMM și raportate pentru IVI-ul ales. A2DP/AVRCP nu sunt tratate ca porturi RFCOMM. Lipsa unei ținte compatibile este neconcludentă, nu un test trecut.
7. Apasă **Attempt secure connection** sau **Attempt unauthenticated connection**. Fiecare apăsare inițiază o singură încercare, limitată la 20 de secunde. Conexiunea se închide după verificare. Aplicația nu trimite și nu citește date de aplicație pe acea conexiune: nu transmite comenzi, contacte, mesaje, fișiere sau payload-ul din modul Lab.
8. Opțional, activează politica **requires a bond after connection** înainte de încercare. Dacă legătura se stabilește, PASS/FAIL se referă strict la existența unei asocieri după conectare. Cu politica dezactivată, succesul se înregistrează ca OBSERVED. O eroare/refuz/expirare rămâne INCONCLUSIVE; nu demonstrează că autorizarea IVI a funcționat.
9. **Observe selected IVI audio / call profiles** citește starea locală A2DP și HFP/Headset pentru mașina selectată. Nu inițiază apeluri și nu redă sunete. Pentru conectarea profilurilor native, folosește setările normale ale telefonului și IVI-ului.
10. Testează manual confirmarea asocierii și refuzul partajării contactelor/mesajelor. **Record manual profile authorization result** cere o observație și permite înregistrarea verdictului operatorului. Raportul marchează explicit că rezultatul provine de la operator, fără verificare independentă prin APK.
11. În **Evidence**, setează rolul **Phone**, notează modelul IVI, firmware-ul, starea inițială de asociere și identificatorii capturilor externe. Salvează și exportă JSON.

## Ce înseamnă rezultatele

- Conectarea transportului nu demonstrează acces la funcțiile sau datele protejate ale IVI-ului.
- Modul „unauthenticated” nu înseamnă automat trafic necriptat sau ocolirea protecției mașinii.
- Publicarea unui serviciu prin SDP nu este, singură, o vulnerabilitate.
- Verificările din **Local** se aplică dispozitivului care rulează aplicația. Pe telefon, nu verifică sandbox-ul IVI. Pentru IVI-02 sunt necesare rularea pe IVI sau instrumentarea separată a bancului.
- **Lab** păstrează protocolul controlat din versiunea 1.0 și necesită aplicația pe ambele dispozitive. Nu folosi mesajele acelui protocol ca payload către serviciile native ale mașinii.

## Instalare și actualizare

Instalează APK-ul din directorul `dist/` sau din release-ul privat. Versiunea 1.1 are codul 2 și este semnată cu cheia locală a versiunii 1.0, deci se poate actualiza peste APK-ul livrat anterior. O reconstrucție din sursă fără cheia originală produce alt certificat și necesită dezinstalarea buildului vechi, cu pierderea datelor locale; exportă rapoartele înainte.

## Limite de validare

Testele automate verifică logica de clasificare a rezultatelor, filtrarea UUID-urilor, limitele protocolului Lab, interfața, permisiunile, comportamentul fără țintă, exportul și persistența. Emulatorul nu înlocuiește testarea conexiunii reale telefon–IVI. Rezultatele fizice și reproducerea unei vulnerabilități Bluetooth concrete necesită vehiculul/bancul și identificarea exactă a implementării și firmware-ului.
