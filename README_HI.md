# HTML Live Wallpaper (Android)

यह शुरुआती Android प्रोजेक्ट वेबसाइट URL या ZIP में दिए गए HTML/CSS/JS को Live Wallpaper के रूप में दिखाने के लिए है।

## अभी क्या काम करता है
- HTTPS/HTTP वेबसाइट URL सेव करना और WebView में Live Wallpaper के रूप में लोड करना।
- ZIP से HTML प्रोजेक्ट import करना; ZIP में `index.html` होना चाहिए।
- ZIP प्रोजेक्ट ऐप के private internal storage में extract होता है, इसलिए ZIP import के बाद ऑफलाइन HTML/स्थानीय assets चल सकते हैं।
- WebView DOM Storage/IndexedDB और default HTTP cache सक्षम हैं।
- Wallpaper अदृश्य होने पर frame loop रोकने की कोशिश करता है।
- शुरुआती renderer 15 FPS पर है।

## जरूरी सीमाएँ (ईमानदारी से)
1. Direct website URL mode वेबसाइट को ऑनलाइन लोड करता है। यह वेबसाइट का पूरा offline mirror नहीं बनाता। Offline चलाने के लिए HTML/CSS/JS/assets वाली ZIP import करें।
2. IndexedDB और WebView cache वेबसाइट के origin और Android WebView के cache व्यवहार पर निर्भर हैं। Cache को Android साफ कर सकता है; असली offline project के लिए ZIP/internal storage बेहतर है।
3. यह पहला working prototype है। WebView को off-screen bitmap में draw करना हर फोन/launcher पर समान प्रदर्शन नहीं देगा। WebGL/Three.js और भारी animations सीमित या असंगत हो सकते हैं।
4. वर्तमान ZIP importer केवल `index.html` खोजता है; project के relative assets उसी folder structure में रखें।
5. ZIP केवल भरोसेमंद स्रोत से लें। इस prototype में ZIP HTML/JS को sandbox नहीं किया गया है।
6. कोई भी animated wallpaper CPU/GPU और बैटरी खर्च करेगा। 15 FPS रखा है; फिर भी भारी CSS filters, बड़े canvas और continuous WebGL से फोन गर्म हो सकता है।
7. इस version में incremental update/version manifest और adjustable FPS/refresh controls अभी शामिल नहीं हैं। इन्हें अगले iteration में जोड़ा जा सकता है।

## ZIP का सही ढाँचा
```text
my-wallpaper.zip
  index.html
  css/
    style.css
  js/
    app.js
  images/
    background.webp
```
ZIP के अंदर एक अतिरिक्त top-level folder हो तो भी importer `index.html` खोज लेगा।

## GitHub से APK बनाने के आसान स्टेप
1. GitHub पर sign in करें।
2. `New repository` दबाएँ।
3. Repository name रखें: `html-live-wallpaper`
4. Public चुनें (Actions setup सरल रखने के लिए), फिर `Create repository` दबाएँ।
5. Repository में `Add file` > `Upload files` चुनें।
6. इस ZIP को अपने कंप्यूटर/मोबाइल में extract करें। ZIP फाइल को सीधे repository में upload न करें; ZIP के अंदर की सभी files और `.github` folder upload करें।
7. ध्यान दें: `.github/workflows/build-apk.yml` भी upload होना चाहिए। अगर hidden `.github` folder दिखाई न दे, तो GitHub web upload की जगह GitHub Desktop या browser में folder contents upload करने का तरीका अपनाएँ। GitHub web UI में folders को drag/drop करने पर पूरा folder structure सुरक्षित रखें।
8. नीचे `Commit changes` दबाएँ।
9. Repository में `Actions` tab खोलें। पहली बार GitHub Actions enable करने का बटन आए तो उसे enable करें।
10. बाईं ओर `Build Android APK` workflow चुनें।
11. `Run workflow` > हरा `Run workflow` दबाएँ।
12. Build पूरा होने तक प्रतीक्षा करें। सफल होने पर हरा check दिखाई देगा।
13. उसी workflow run के नीचे `Artifacts` में `HTML-Live-Wallpaper-debug-apk` मिलेगा। उसे डाउनलोड करें।
14. डाउनलोड ZIP को extract करें। अंदर `app-debug.apk` होगा।
15. APK मोबाइल में भेजकर install करें। Android पूछे तो उस source से install की अनुमति दें।
16. ऐप खोलें, URL सेव करें या ZIP import करें, फिर `Live Wallpaper सेट करें` दबाएँ।

## GitHub Actions में build failure हो तो
- `Actions` > failed run > `Build debug APK` खोलें।
- लाल error के आखिरी 20-30 lines कॉपी करके ChatGPT में भेजें।
- APK तभी डाउनलोड करें जब workflow सफल हो।

## Security
यह demo app वेबसाइट के HTML/JavaScript को चलाता है। केवल भरोसेमंद URL/ZIP का उपयोग करें। अज्ञात ZIP में malicious JavaScript हो सकता है।
