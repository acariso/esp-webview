package com.example.araba;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean isListening = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Button btnStartVoice, btnStopVoice;
    private static final String HTML_URL = "https://raw.githubusercontent.com/acariso/asdasd/refs/heads/main/esp.html";
    private Runnable listenTimeoutRunnable = null;
    private static final int LISTEN_TIMEOUT_MS = 2100;
    private static final int SHORT_DELAY_MS = 1000;

    // GEMINI API
    private static final String GEMINI_API_KEY = "AIzaSyCZUBLimLVpwfv7b4kJ3hrx0zhYq2ANb94";
    private static final String GEMINI_MODEL = "gemini-1.5-flash-latest";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent?key=" + GEMINI_API_KEY;

    // Sohbet geçmişini tutacak liste
    private List<JSONObject> chatHistory = new ArrayList<>();

    // Robot Kontrol Prompt'u
    private static final JSONObject ROBOT_PROMPT_INITIAL_MESSAGE;

    static {
        try {
            ROBOT_PROMPT_INITIAL_MESSAGE = new JSONObject();
            ROBOT_PROMPT_INITIAL_MESSAGE.put("role", "user");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text",
                    "Sen bir araba kontrol asistanısın. Senin esas ve tek görevin, kullanıcıdan gelen herhangi bir doğal dildeki (çok dilli destekli) sözlü komutları olağanüstü bir dikkatle ve titizlikle analiz etmek, ardından bu komutları, aşağıda istisnasız bir şekilde detaylıca listelenmiş olan önceden tanımlanmış, kesin, tekil ve harfiyen aynı araba kontrol komutlarından birine dönüştürmektir. Bu dönüşüm sürecinde, üretilen komut çıktısını en saf, en yalın ve en eksiksiz haliyle sunmalısın. Bu, şu anlama geliyor: çıktın, asla hiçbir ek bilgi, açıklama, uyarı, selamlama, veda, fazla kelime, noktalama işareti, özel karakter, emoji veya gereksiz boşluk içermemelidir; çıktın sadece ve sadece o tekil, saf ve önceden tanımlanmış komut olmalıdır.\n\n" +
                            "**KIRMIZI ÇİZGİ - EN ÖNEMLİ KURAL:** Eğer kullanıcıdan gelen girdi, aşağıda belirtilen tanımlanmış araba kontrol komutları listesinden **herhangi birine kesinlikle uymuyorsa** (örneğin, bir soru sorarsa, sohbet etmeye çalışırsa, genel bir ifade kullanırsa, teşekkür ederse, alakasız bir cümle kurarsa, veya tanımlı bir komuta benzemeyen bir istekte bulunursa), **kesinlikle ama kesinlikle hiçbir çıktı verme.** Bu durumda, yanıt alanının **tamamen boş** kalması gerekmektedir. **Boş bir metin dizesi, bir satır atlaması, herhangi bir boşluk veya tırnak işareti (\"\") dahil olmak üzere hiçbir karakter dahi döndürme.** Bu, sistemin bu tür durumlarda tamamen sessiz kalması ve yanlış/gereksiz yanıtlar üretmemesi için hayati öneme sahiptir.\n\n" +
                            "--- \n**Desteklenen Araba Özellikleri ve Komutları (Kapsamlı Liste)**\n--- \n" +
                            "Aşağıda, asistan olarak tanıyıp işleyebileceğin ve araba kontrolüne doğrudan etki eden her bir komut, beklenen ifade biçimleri ve ilgili fonksiyonları ile birlikte listelenmiştir. Bu komutlar, kullanıcının niyetini en net şekilde yansıtan anahtar kelimeler ve yapılarla ifade edilmiştir:\n\n" +
                            "* **ileri**: Bu komut verildiğinde, araç kesinlikle ve kesintisizce düz bir hatta ileri doğru hareket etmeye başlar. Kullanıcılar bu niyeti ifade etmek için \"ön\", \"ilerle\", \"ileri git\", \"araba öne doğru gitsin\", \"hızlıca ileri\" gibi çeşitli ifadeler de kullanabilirler. Asıl niyet her zaman \"ileri\" harekettir.\n" +
                            "* **geri**: Bu komut, arabanın güvenli bir şekilde geriye doğru hareket etmesini sağlar. \"Geriye git\", \"geri gel\", \"araba geriye doğru gelsin\", \"yavaşça geri\" gibi ifadelerle de tetiklenebilir.\n" +
                            "* **sağ**: Bu komut, aracın belirgin bir şekilde sağa doğru dönmesini veya yön değiştirmesini emreder. Kullanıcılar \"sağa dön\", \"sağa çevir\", \"araba sağa doğru dönsün\", \"direksiyonu sağa kır\" gibi ifadeler kullanabilirler. **Türkçe karakter desteği nedeniyle, sesli tanıma sistemlerinden veya farklı klavye düzenlerinden \"sağ\" kelimesinin \"sag\" olarak da gelebileceğini kesinlikle unutma ve bu durumu öngör.** Her iki yazılış veya telaffuz (sağ, sag) da kullanıcının mutlak suretle sağa dönme niyetini belirtiyorsa, çıktıyı her zaman ve sadece **\"sağ\"** olarak ver. Bu, sesli tanıma sistemlerinin veya dil modellerinin Türkçe karakterleri bazen yanlış yorumlamasından kaynaklanabilecek potansiyel hataları telafi etmek için kritik bir kuraldır. Niyet her zaman ve her koşulda \"sağ\" yöne gitmek veya dönmektir.\n" +
                            "* **sol**: Bu komut, aracın belirgin bir şekilde sola doğru dönmesini veya yön değiştirmesini sağlar. \"Sola dön\", \"sola çevir\", \"araba sola doğru gitsin\", \"direksiyonu sola döndür\" gibi ifadelerle de tetiklenebilir.\n" +
                            "* **dur**: Bu komut, aracın anında ve güvenli bir şekilde tüm hareketini keserek tamamen durmasını sağlar. \"Durdur\", \"fren yap\", \"araba dursun\", \"hemen dur\", \"aracı durdur\" gibi çeşitli ve net ifadelerle de kullanılabilir.\n" +
                            "* **turbo aç**: Bu komut, aracın \"turbo\" modunu veya hız artırma özelliğini etkinleştirir. Bu modda araç belirgin şekilde daha hızlı hareket eder. Kullanıcılar \"hızlandır\", \"daha hızlı git\", \"turbo özelliğini aç\", \"hız moduna geç\" gibi ifadelerle de bu niyeti mutlak suretle belirtebilir.\n" +
                            "* **turbo kapat**: Bu komut, aktif olan \"turbo\" modunu veya hızlandırma özelliğini devre dışı bırakır ve aracın normal sürüş hızına dönmesini sağlar. \"Turboyu kapat\", \"hızlı modu kapat\", \"turbo devre dışı bırak\" gibi ifadeler de kullanılabilir.\n" +
                            "* **su fışkırt**: Bu komut, aracın üzerinde bulunan su pompasını çalıştırarak belirlenmiş bir alana su püskürtmesini sağlar. \"Su sık\", \"su at\", \"pompayı çalıştır\", \"su püskürt\" gibi ifadeler de aynı anlama gelir ve bu komutu tetikleyecektir.\n" +
                            "* **servoyu sıfırla**: Bu komut, servo motoru mutlak olarak orta (0) noktasına, yani varsayılan başlangıç pozisyonuna geri getirir. \"Servoyu orta konuma al\", \"servoyu resetle\", \"servo ayarını sıfırla\" gibi ifadeler de bu komutu tetikler.\n" +
                            "* **servoyu X derece yap**: Bu komut, servo motorun belirli bir açıya mutlak suretle dönmesini sağlar. Buradaki **`X` değeri, mutlak olarak -90 ile +90 derece arasında olmalıdır**. Ve **yalnızca** şu önceden tanımlanmış, izin verilen spesifik açılardan biri olabilir: **-90, -60, -30, 0, 30, 60 veya 90**. Kullanıcı, bu listede olmayan farklı bir açı (örn. 45 derece, 10 derece, -20 derece) veya aralık dışı bir açı (örn. -100 derece, 120 derece) verse bile, bu açıya **en yakın olan** yukarıdaki listeden bir değer seçilerek işlem yapılmalıdır. Yuvarlama işlemi, belirtilen değere **mutlak farkı en az olan** geçerli değeri bulma prensibine göre yapılmalıdır. Örneğin:\n" +
                            "    * \"servoyu 45 derece yap\" dendiğinde: 45'e en yakın desteklenen açılar 30 (fark 15) ve 60 (fark 15). Bu durumda, genelde pozitif yöndeki en yakın değer olan **60** tercih edilir veya mutlak fark en az olan seçilir. Sonuç: \"servoyu 60 derece yap\".\n" +
                            "    * \"servoyu 10 derece yap\" dendiğinde: 10'a en yakın desteklenen açı 0'dır (fark 10). Sonuç: \"servoyu 0 derece yap\".\n" +
                            "    * \"servoyu -25 derece yap\" dendiğinde: -25'e en yakın desteklenen açı -30'dur (fark 5). Sonuç: \"servoyu -30 derece yap\".\n" +
                            "    * \"servoyu -100 derece yap\" dendiğinde: -100 derece, izin verilen aralığın (-90 ile +90) dışındadır. Bu durumda, aralıktaki en yakın sınır olan -90'a yuvarlanmalıdır. Sonuç: \"servoyu -90 derece yap\".\n" +
                            "    * \"servoyu 120 derece yap\" dendiğinde: 120 derece, izin verilen aralığın dışındadır. Bu durumda, aralıktaki en yakın sınır olan 90'a yuvarlanmalıdır. Sonuç: \"servoyu 90 derece yap\".\n" +
                            "    Yuvarlama veya düzeltme işleminden sonra, çıktıyı kesinlikle ve sadece \"servoyu [yuvarlanmış_X] derece yap\" formatında vermelisin. Başka hiçbir kelime veya ekleme kabul edilemez.\n\n" +
                            "--- \n**Komut Analiz ve Karar Verme Mekanizması Kuralları (Evrensel Uygulama - Aşırı Detaylı)**\n--- \n" +
                            "Asistan olarak, kullanıcıdan gelen her sesli veya yazılı girdiyi, aşağıdaki adımlara göre mutlak bir hassasiyetle analiz etmeli ve bir karar vermelisin. Bu kurallara istisnasız uyulmalıdır:\n\n" +
                            "1.  **Niyetin Derinlemesine ve Dil Bağımsız Anlaşılması:** Gelen cümlenin dilinden (Türkçe, İngilizce, Almanca, İspanyolca, Fransızca, Rusça vb. fark etmeksizin) bağımsız olarak, kullanıcının asıl niyetini ve arabadan beklediği eylemi kusursuzca kavramaya çalış. Kullanıcının dile getirdiği kelimeleri, cümlenin genel akışını, olası eşanlamlıları ve bağlamı (ancak sadece araba kontrolüyle ilgili bağlamı) değerlendirerek en doğru niyeti ve hedef komutu belirle. Sözlük, çeviri veya dil modelleme yeteneklerini bu niyet tespitinde kullan.\n" +
                            "2.  **Tek Komut Prensibi - Katı Önceliklendirme:** Eğer kullanıcının cümlesi birden fazla olası araba kontrol komutunu açıkça veya dolaylı olarak içeriyorsa (örn. \"Araba ileri gitsin ve sonra da sağa dönsün\" veya \"Dur ve turbo aç\"), **kesinlikle ve yalnızca cümlenin başlangıcında yer alan veya anlamsal olarak en baskın olan ilk komutu işle**. Diğer tüm komutları, istekleri veya ifadeleri tamamen yok say ve çıktına hiçbir şekilde dahil etme. Bu, sistemin kararlı, öngörülebilir ve güvenli çalışmasını sağlamak için mutlak bir kuraldır.\n" +
                            "3.  **Kesin Eşleşme ve Anahtar Kelime Tespiti (Varyasyonları ile):** Kullanıcının söylediği ifadenin, yukarıda listelenen **önceden tanımlanmış araba kontrol komutları listesindeki bir anahtar kelime veya kalıpla (örn. \"ileri\", \"dur\", \"turbo aç\", \"servoyu X derece yap\") tam ve mutlak olarak eşleştiğinden** emin ol. Eşleşme, sadece kelime bazında değil, aynı zamanda anlam ve fonksiyon olarak da tam olmalıdır. Anlamsal olarak eşleşen eşanlamlı ifadeleri tanıma yeteneğin olmalı, ancak bu, listedeki bir komuta indirgenemeyecek genel ifadelere yayılmamalıdır.\n\n" +
                            "    * **\"Sağ\" Komutuna Özel ve Çok Önemli Dikkat:** Özellikle **\"sağ\" komutu için, çeşitli ses tanıma sistemlerinden veya dil işleme süreçlerinden \"sag\" şeklinde bir telaffuz veya yazım hatası gelebileceğini her zaman göz önünde bulundur**. Hem \"sağ\" (Türkçe karakterli) hem de \"sag\" (Türkçe karaktersiz) kelimeleri, kullanıcının mutlak suretle sağa dönme niyetini belirtiyorsa, her ikisini de istisnasız bir şekilde **\"sağ\" komutuna eşleştir** ve çıktıyı kesinlikle ve sadece **\"sağ\"** olarak ver. Bu kural, dil işleme sırasındaki Türkçe karakter uyumsuzluklarından kaynaklanabilecek her türlü hatayı telafi etmek için hayati bir öneme sahiptir ve bu duruma özellikle dikkat edilmelidir.\n" +
                            "4.  **Servo Açı Ayarı İçin Hassas ve Kurallı Yuvarlama Prosedürü:**\n" +
                            "    * Kullanıcı servo motoru için \"servoyu X derece yap\" formatında bir komut verdiğinde, `X` değeri, doğrudan veya dolaylı olarak telaffuz edilmiş veya yazılmış olsun, **-90 ile +90 derece arasındaki geçerli aralıkta olup olmadığını mutlak suretle kontrol et**. Bu aralık dışındaki değerler geçersizdir.\n" +
                            "    * Eğer kullanıcının belirttiği `X` değeri bu geçerli aralığın dışındaysa (örn. -100, 120, 200), `X`'i aralığın en yakın sınırına yuvarla. Yani, -90'dan küçükse -90'a, 90'dan büyükse 90'a yuvarla.\n" +
                            "    * Eğer `X` değeri geçerli aralık içinde, ancak yukarıda belirtilen desteklenen spesifik değerler listesinde (-90, -60, -30, 0, 30, 60, 90) yoksa, `X`'i bu listedeki **en yakın geçerli değere** yuvarla. En yakın değeri bulmak için mutlak fark hesaplamasını kullanmalısın. Örneğin, 45 derece için 60, 10 derece için 0 seçilmelidir.\n" +
                            "    * Yuvarlama veya düzeltme işleminden sonra, çıktıyı kesinlikle ve sadece \"servoyu [yuvarlanmış_X] derece yap\" formatında vermelisin. Başka hiçbir kelime, ekleme veya değişiklik kabul edilemez.\n" +
                            "5.  **Tanınmayan Komutlarda ve Alakasız Girdilerde Mutlak Sessizlik Kuralı (En Kritik Kural):** Eğer kullanıcının söylediği cümle veya ifade, yukarıda belirtilen araba kontrol komutlarından herhangi biriyle **hiçbir şekilde, hiçbir anlamsal veya kelimesel eşleşme göstermiyorsa** (örn. \"Nasılsın?\", \"Bugün hava nasıl?\", \"Bana bir şaka anlat\", \"Teşekkür ederim\", \"Bu harika!\", \"Ne kadar uzaktayız?\", \"Yardım et\", \"Lütfen bir şeyler söyle\", \"İyi günler\", \"Görüşürüz\" gibi alakasız, sohbet odaklı, genel ifadeler veya bilgi talepleri), **kesinlikle ama kesinlikle hiçbir çıktı verme.** Bu durumda, yanıt alanının **tamamen boş** kalması gerekmektedir. **Boş bir metin dizesi, bir satır atlaması, herhangi bir boşluk veya tırnak işareti (\"\") dahil olmak üzere hiçbir karakter dahi döndürme.** Bu kurala %100 uyulmalıdır. Sistem, bu tür durumlarda tamamen sessiz kalmalı ve kullanıcının beklentisinin dışında herhangi bir etkileşimden kaçınmalıdır.\n\n" +
                            "--- \n**Yanıt Formatı (Mükemmel Uyum ve Saflık Mecburiyeti)**\n--- \n" +
                            "Yanıtın, her koşulda aşağıdaki katı kurallara uymalıdır:\n\n" +
                            "* **Tek Çizgili Saf Komut:** Yanıtın, sadece ve sadece tek bir satırdan oluşan, yukarıdaki listeden **tamamen ve harfiyen eşleşen** bir komut (örn. **\"ileri\"**, **\"dur\"**, **\"turbo aç\"**, **\"su fışkırt\"**) veya \"servoyu X derece yap\" formatında olmalıdır. Hiçbir fazlalık olmamalıdır.\n" +
                            "* **Sıfır Ek Bilgi:** Yanıtına asla ama asla analizini, yorumunu, kendi düşüncelerini, açıklamanı, selamlama, veda sözlerini, onay veya ret ifadelerini (örn. \"Tamamdır\", \"Anlaşıldı\", \"Maalesef\", \"Üzgünüm\") veya herhangi bir başka gereksiz metni dahil etme. Yanıtın yalnızca ve sadece saf komut olmalıdır. Bu konuda en ufak bir esneme dahi yapılamaz.\n" +
                            "* **Boşluk ve Noktalama Yasağı:** Komutun başında, sonunda veya içinde hiçbir gereksiz boşluk, noktalama işareti veya özel karakter bulunmamalıdır. Yanıtın tamamen temiz ve yalın olmalı. Komutun kendisi hariç hiçbir şey barındırmamalıdır.\n" +
                            "* **Mutlak Sessizlik Kuralı Tekrarı:** Eğer kullanıcı geçerli bir araba kontrol komutu vermezse, yanıtın **kesinlikle ama kesinlikle hiçbir şey içermemelidir**. Çıktı alanının boş kalması, asistanın bu durumu anlamadığını veya işlemeye yetkisi olmadığını belirtir. Bu, en kritik kuraldır ve %100 uyulması zorunludur.\n\n" +
                            "**Örnek Analizler ve Kesin Beklenen Yanıtlar (Çok Sayıda ve Detaylı Türkçe Örnekler):**\n\n" +
                            "* **Kullanıcı:** \"Arabayı bir miktar ileri doğru hareket ettirir misin?\"\n    * **Analiz:** Kullanıcının temel niyeti arabayı **ileri** hareket ettirmek. \"bir miktar\" ve \"doğru\" gibi ifadeler ek bilgilerdir ve yok sayılır.\n    * **Beklenen Yanıt:** ileri\n\n" +
                            "* **Kullanıcı:** \"Geriye gitmeye başla hemen, acele etmeliyiz.\"\n    * **Analiz:** Kullanıcının ana komutu **geri** gitmek. \"Hemen, acele etmeliyiz\" gibi ifadeler ek açıklamalardır.\n    * **Beklenen Yanıt:** geri\n\n" +
                            "* **Kullanıcı:** \"Şu anda sağa dönmemiz gerekiyor, lütfen acele et!\"\n    * **Analiz:** Kullanıcının niyeti **sağa** dönmek. \"Şu anda\", \"lütfen\", \"acele et!\" ek bilgilerdir.\n    * **Beklenen Yanıt:** sağ\n\n" +
                            "* **Kullanıcı:** \"Sag tarafa dönelim hemen, acele var.\"\n    * **Analiz:** Kullanıcının niyeti **sağa** dönmek. \"Sag\" Türkçe karakter uyumsuzluğundan kaynaklı bir varyasyondur ama niyet \"sağ\" dır. Diğerleri ek bilgidir.\n    * **Beklenen Yanıt:** sağ\n\n" +
                            "* **Kullanıcı:** \"Sola doğru hafifçe bir dönüş yapabilir misin?\"\n    * **Analiz:** Kullanıcının niyeti **sola** dönmek. \"Hafifçe\" ve \"bir dönüş yapabilir misin\" ek ifadelerdir.\n    * **Beklenen Yanıt:** sol\n\n" +
                            "* **Kullanıcı:** \"Araba olduğu yerde dursun, artık gitmeyelim.\"\n    * **Analiz:** Kullanıcının komutu **dur**. \"Olduğu yerde\", \"artık gitmeyelim\" ek bilgilerdir.\n    * **Beklenen Yanıt:** dur\n\n" +
                            "* **Kullanıcı:** \"Turbo modunu etkinleştirebilir miyiz acaba?\"\n    * **Analiz:** Kullanıcının niyeti **turbo aç** komutunu vermek. \"Etkinleştirebilir miyiz acaba\" bir sorudur ama niyet açıktır.\n    * **Beklenen Yanıt:** turbo aç\n\n" +
                            "* **Kullanıcı:** \"Şu turbo özelliğini artık tamamen kapatalım.\"\n    * **Analiz:** Kullanıcının niyeti **turbo kapat** komutunu vermek. \"Şu\" ve \"artık tamamen\" ek kelimelerdir.\n    * **Beklenen Yanıt:** turbo kapat\n\n" +
                            "* **Kullanıcı:** \"Su pompasını çalıştır, camlar çok kirlendi ve göremiyorum.\"\n    * **Analiz:** Kullanıcının komutu **su fışkırt**. \"Camlar çok kirlendi ve göremiyorum\" bir sebeptir ve yok sayılır.\n    * **Beklenen Yanıt:** su fışkırt\n\n" +
                            "* **Kullanıcı:** \"Servoyu tam orta noktaya sıfırla lütfen, ayarları bozuldu sanırım.\"\n    * **Analiz:** Kullanıcının komutu **servoyu sıfırla**. \"Tam orta noktaya\" ve \"ayarları bozuldu sanırım\" ek açıklamalardır.\n    * **Beklenen Yanıt:** servoyu sıfırla\n\n" +
                            "* **Kullanıcı:** \"Servoyu 60 dereceye ayarla şimdi, bu açı ideal.\"\n    * **Analiz:** Kullanıcının komutu **servoyu 60 derece yap**. \"Şimdi\" ve \"bu açı ideal\" ek kelimelerdir.\n    * **Beklenen Yanıt:** servoyu 60 derece yap\n\n" +
                            "* **Kullanıcı:** \"Servoyu eksi otuz yapabilir misin? Çok önemli.\"\n    * **Analiz:** Kullanıcının niyeti **servoyu -30 derece yap** komutunu vermek. \"Eksi otuz\" ifadesi -30'a karşılık gelir. \"Çok önemli\" ek bilgidir.\n    * **Beklenen Yanıt:** servoyu -30 derece yap\n\n" +
                            "* **Kullanıcı:** \"Servoyu 15 dereceye getir lütfen, bu ayar lazım.\"\n    * **Analiz:** 15 derece desteklenmeyen bir açıdır. En yakın desteklenen değer 0'dır. \"Lütfen, bu ayar lazım\" ek bilgilerdir.\n    * **Beklenen Yanıt:** servoyu 0 derece yap\n\n" +
                            "* **Kullanıcı:** \"Servoyu 70 yapabilir miyiz? Sanırım tam ortada.\"\n    * **Analiz:** 70 derece desteklenmiyor. En yakın desteklenen değer 60'tır. \"Sanırım tam ortada\" ek bilgidir.\n    * **Beklenen Yanıt:** servoyu 60 derece yap\n\n" +
                            "* **Kullanıcı:** \"Araba ileri gitmeye başlasın ve sonra da sola dönsün, sonra da dursun.\"\n    * **Analiz:** Birden fazla komut var (\"ileri git\", \"sola dönsün\", \"dursun\"). Sadece ilkini işle: \"ileri git\". Diğerleri yok sayılır.\n    * **Beklenen Yanıt:** ileri\n\n" +
                            "* **Kullanıcı:** \"Nasılsın bugün, her şey yolunda mı?\"\n    * **Analiz:** Bu bir araba kontrol komutu değil, genel bir selamlaşma/soru. Tanımlı komutlarla eşleşmiyor.\n    * **Beklenen Yanıt:** \n\n" +
                            "* **Kullanıcı:** \"Bana hava durumunu söyleyebilir misin Ankara için?\"\n    * **Analiz:** Bu bir araba kontrol komutu değil, bir bilgi talebi. Tanımlı komutlarla eşleşmiyor.\n    * **Beklenen Yanıt:** \n\n" +
                            "* **Kullanıcı:** \"Teşekkürler, çok işime yaradın, harikasın.\"\n    * **Analiz:** Bu bir teşekkür ve övgü ifadesi, komut değil. Tanımlı komutlarla eşleşmiyor.\n    * **Beklenen Yanıt:** \n\n" +
                            "* **Kullanıcı:** \"Arabayı park eder misin şimdi?\"\n    * **Analiz:** \"Park et\" komutu tanımlı komutlar arasında yok. Her ne kadar bir araba eylemi olsa da, desteklenenler listesinde değil.\n    * **Beklenen Yanıt:** \n\n" +
                            "* **Kullanıcı:** \"Ne kadar hızlı gidiyoruz şu an?\"\n    * **Analiz:** Bu bir soru, komut değil. Tanımlı komutlarla eşleşmiyor.\n    * **Beklenen Yanıt:** \n\n" +
                            "* **Kullanıcı:** \"Merhaba, lütfen ileri git ve sonra da dur.\"\n    * **Analiz:** \"Merhaba\" bir selamlama ve ilk komut \"ileri git\". Çoklu komut kuralına göre sadece ilk geçerli komut alınır.\n    * **Beklenen Yanıt:** ileri\n\n" +
                            "* **Kullanıcı:** \"Lütfen turbo modunu kapatır mısın? Çok hızlı gidiyor ve kontrol edemiyorum.\"\n    * **Analiz:** \"Çok hızlı gidiyor ve kontrol edemiyorum\" ek bilgi ve sebep, temel komut \"turbo modunu kapat\".\n    * **Beklenen Yanıt:** turbo kapat\n\n" +
                            "* **Kullanıcı:** \"Arabayı biraz sola doğru çevirir misin, öyle daha iyi olacak.\"\n    * **Analiz:** \"biraz\", \"doğru\" ve \"öyle daha iyi olacak\" ek bilgiler, temel komut \"sola çevir\".\n    * **Beklenen Yanıt:** sol\n\n" +
                            "* **Kullanıcı:** \"Servoyu -25 dereceye ayarla şimdi, bu ayar doğru olmalı.\"\n    * **Analiz:** -25 derece desteklenmiyor, en yakın değer -30. Diğerleri ek bilgidir.\n    * **Beklenen Yanıt:** servoyu -30 derece yap\n\n" +
                            "* **Kullanıcı:** \"Servoyu 10 derece yap lütfen, burası çok önemli.\"\n    * **Analiz:** 10 derece desteklenmiyor, en yakın değer 0. Diğerleri ek bilgidir.\n    * **Beklenen Yanıt:** servoyu 0 derece yap\n\n" +
                            "* **Kullanıcı:** \"Araba dur lütfen, çok hızlı gidiyoruz ve çarpmak üzereyiz.\"\n    * **Analiz:** \"Çok hızlı gidiyoruz ve çarpmak üzereyiz\" ek bilgi, temel komut \"dur\".\n    * **Beklenen Yanıt:** dur\n\n" +
                            "* **Kullanıcı:** \"Su fışkırtır mısın acaba? Camlar çok kirlendiği için önümü göremiyorum.\"\n    * **Analiz:** \"Camlar çok kirlendiği için önümü göremiyorum\" ek bilgi, temel komut \"su fışkırt\".\n    * **Beklenen Yanıt:** su fışkırt\n\n" +
                            "* **Kullanıcı:** \"Servoyu sıfırla lütfen, pozisyonu kayboldu ve yanlış yerde duruyor.\"\n    * **Analiz:** \"Pozisyonu kayboldu ve yanlış yerde duruyor\" ek bilgi, temel komut \"servoyu sıfırla\".\n    * **Beklenen Yanıt:** servoyu sıfırla"
            ));
            ROBOT_PROMPT_INITIAL_MESSAGE.put("parts", parts);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to create initial prompt message.", e);
        }
    }

    // Komutları daha düzenli tutmak için sabitler tanımlayalım (Gemini'nin döndüreceği format - PROMPT İLE UYUMLU OLARAK GÜNCELLENDİ)
    private static final String COMMAND_ILERI = "ileri";
    private static final String COMMAND_GERI = "geri";
    private static final String COMMAND_SAG = "sağ";
    private static final String COMMAND_SOL = "sol";
    private static final String COMMAND_DUR = "dur";
    private static final String COMMAND_TURBO_AC = "turbo aç";
    private static final String COMMAND_TURBO_KAPAT = "turbo kapat";
    private static final String COMMAND_SU_FISKIRT = "su fışkırt";
    // NOTE: Yeni prompt'ta "su durdur" komutu için bir örnek veya çıktı formatı yok.
    // Eğer böyle bir komutunuz varsa, prompt'a ve COMMAND_SU_DURDUR'a eklemelisiniz.
    // Şimdilik sadece "su fışkırt" ile devam ediyorum.
    // private static final String COMMAND_SU_DURDUR = "su durdur"; // Eğer prompt'a eklerseniz, bu satırı aktif edin.
    private static final String COMMAND_SERVO_SIFIRLA = "servoyu sıfırla";
    // Servo derece komutu için özel regex kullanılır, sabit tanımlamaya gerek yok.


    // Sesli geri bildirimler için sabitler (eskisi gibi kalabilir, prompt çıktısı ile alakası yok)
    private static final String SPEAK_ASISTANT_STARTED = "Sesli asistan başlatıldı. Komutunuzu bekliyorum.";
    private static final String SPEAK_ASISTANT_STOPPED = "Sesli asistan durduruldu.";
    private static final String SPEAK_GOING_FORWARD = "İleri gidiyorum.";
    private static final String SPEAK_GOING_BACKWARD = "Geri gidiyorum.";
    private static final String SPEAK_TURNING_RIGHT = "Sağa dönüyorum.";
    private static final String SPEAK_TURNING_LEFT = "Sola dönüyorum.";
    private static final String SPEAK_STOPPING = "Duruyorum.";
    private static final String SPEAK_TURBO_ON = "Turbo modu açıldı.";
    private static final String SPEAK_TURBO_OFF = "Turbo modu kapatıldı.";
    private static final String SPEAK_WATER_SPRAYING = "Su fışkırtılıyor.";
    private static final String SPEAK_WATER_STOPPED = "Su fışkırtma durduruldu."; // Eğer "su durdur" komutu aktifse kullanılır.
    private static final String SPEAK_SERVO_RESET = "Servo sıfırlandı.";
    private static final String SPEAK_GEMINI_CONNECTION_ERROR = "İletişim hatası.";
    private static final String SPEAK_GEMINI_RESPONSE_ERROR = "Yanıt işlenemedi.";
    private static final String SPEAK_GEMINI_NO_RESPONSE = "Yanıt alınamadı.";
    private static final String SPEAK_GEMINI_REQUEST_FAIL = "Asistan meşgul.";
    private static final String SPEAK_LISTENING_FAILED = "Dinleme başlatılamadı.";
    private static final String SPEAK_NO_VOICE_SUPPORT = "Cihazda sesli komut desteği yok.";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        makeFullScreen();
        checkPermissions();

        webView = findViewById(R.id.webView);
        btnStartVoice = findViewById(R.id.btnStartVoice);
        btnStopVoice = findViewById(R.id.btnStopVoice);

        initWebView();
        initTextToSpeech();
        initSpeechRecognizer();

        btnStartVoice.setOnClickListener(v -> startVoiceAssistant());
        btnStopVoice.setOnClickListener(v -> stopVoiceAssistant());
    }

    private void makeFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void initWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // HTML yüklendiğinde başlangıç müziğini çal
                MediaPlayer mediaPlayer = MediaPlayer.create(view.getContext(), R.raw.oysapanelgiris);
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        });

        fetchAndShowHtml(); // HTML'i GitHub'dan çekip göster
    }

    private void fetchAndShowHtml() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(HTML_URL).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "HTML dosyası alınamadı. İnternet bağlantınızı kontrol edin.", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    runOnUiThread(() -> webView.loadDataWithBaseURL(
                            HTML_URL, // Base URL olarak GitHub URL'sini kullan
                            html,
                            "text/html",
                            "UTF-8",
                            null
                    ));
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "HTML dosyası alınamadı: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(new Locale("tr", "TR"));
            } else {
                Toast.makeText(MainActivity.this, "TextToSpeech başlatılamadı.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    startListenTimeout();
                }

                @Override
                public void onBeginningOfSpeech() {
                    resetListenTimeout();
                }

                @Override
                public void onRmsChanged(float rmsdB) {}
                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    stopListenTimeout();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}
                @Override
                public void onEvent(int eventType, Bundle params) {}

                @Override
                public void onError(int error) {
                    stopListenTimeout();
                    if (isListening) {
                        if (speechRecognizer != null) {
                            speechRecognizer.cancel();
                            speechRecognizer.destroy();
                            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
                            speechRecognizer.setRecognitionListener(this);
                        }
                        handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
                    }
                    Log.e("SpeechRecognizer", "Error: " + error);
                }

                @Override
                public void onResults(Bundle results) {
                    stopListenTimeout();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        Log.d("SpeechRecognizer", "Recognized: " + recognizedText);
                        sendCommandToGemini(recognizedText);
                    }
                }
            });
        } else {
            Toast.makeText(this, SPEAK_NO_VOICE_SUPPORT, Toast.LENGTH_LONG).show();
        }
    }

    private void startListenTimeout() {
        stopListenTimeout();
        listenTimeoutRunnable = () -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.stopListening();
                    if(isListening) {
                        handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
                    }
                }
            } catch (Exception ignored) {
                // Hata durumunda bir şey yapma, loglama isteyebilirsiniz
            }
        };
        handler.postDelayed(listenTimeoutRunnable, LISTEN_TIMEOUT_MS);
    }

    private void resetListenTimeout() {
        stopListenTimeout();
        startListenTimeout();
    }

    private void stopListenTimeout() {
        if (listenTimeoutRunnable != null) {
            handler.removeCallbacks(listenTimeoutRunnable);
            listenTimeoutRunnable = null;
        }
    }

    private void startListening() {
        if (speechRecognizer != null && isListening) {
            try {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                Toast.makeText(this, SPEAK_LISTENING_FAILED, Toast.LENGTH_SHORT).show();
                Log.e("VoiceAssistant", "Failed to start listening: " + e.getMessage());
                stopVoiceAssistant();
            }
        }
    }

    private void speak(String text) {
        if (speechRecognizer != null && isListening) {
            speechRecognizer.cancel();
            stopListenTimeout();
        }

        String utteranceId = "tts_speech_finished_" + System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String s) {}
                @Override
                public void onDone(String s) {
                    if (s.equals(utteranceId)) {
                        handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
                        textToSpeech.setOnUtteranceProgressListener(null);
                    }
                }
                @Override
                public void onError(String s) {
                    if (s.equals(utteranceId)) {
                        handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
                        textToSpeech.setOnUtteranceProgressListener(null);
                    }
                }
                @Override
                public void onStop(String s, boolean interrupted) {
                    if (s.equals(utteranceId)) {
                        handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
                        textToSpeech.setOnUtteranceProgressListener(null);
                    }
                }
            });
        }

        Bundle speechParams = new Bundle();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, speechParams, utteranceId);
    }


    private void startVoiceAssistant() {
        if (!isListening) {
            isListening = true;
            String startUtteranceId = "assistant_start_speech_" + System.currentTimeMillis();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String s) {}
                    @Override
                    public void onDone(String s) {
                        if (s.equals(startUtteranceId)) {
                            handler.post(MainActivity.this::startListening);
                            textToSpeech.setOnUtteranceProgressListener(null);
                        }
                    }
                    @Override
                    public void onError(String s) {
                        if (s.equals(startUtteranceId)) {
                            handler.post(MainActivity.this::startListening);
                            textToSpeech.setOnUtteranceProgressListener(null);
                        }
                    }
                    @Override
                    public void onStop(String s, boolean interrupted) {
                        if (s.equals(startUtteranceId)) {
                            handler.post(MainActivity.this::startListening);
                            textToSpeech.setOnUtteranceProgressListener(null);
                        }
                    }
                });
            }

            Bundle speechParams = new Bundle();
            textToSpeech.speak(SPEAK_ASISTANT_STARTED, TextToSpeech.QUEUE_FLUSH, speechParams, startUtteranceId);
        }
    }


    private void stopVoiceAssistant() {
        if (isListening) {
            isListening = false;
            if (speechRecognizer != null) speechRecognizer.cancel();
            stopListenTimeout();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech.setOnUtteranceProgressListener(null);
            }
            speak(SPEAK_ASISTANT_STOPPED);
        }
    }

    // --- GEMINI ENTEGRASYONU ---
    private void sendCommandToGemini(String userCommand) {
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/json");

        try {
            // Kullanıcının mesajını chatHistory'ye ekle
            JSONObject newUserTurn = new JSONObject();
            newUserTurn.put("role", "user");
            JSONArray newUserParts = new JSONArray();
            newUserParts.put(new JSONObject().put("text", userCommand));
            newUserTurn.put("parts", newUserParts);
            chatHistory.add(newUserTurn);

            List<JSONObject> currentConversation = new ArrayList<>();
            currentConversation.add(ROBOT_PROMPT_INITIAL_MESSAGE);
            currentConversation.addAll(chatHistory);

            JSONObject jsonBody = new JSONObject();
            JSONArray contents = new JSONArray();

            for (JSONObject message : currentConversation) {
                String role = message.getString("role").equals("assistant") ? "model" : message.getString("role");
                String text = message.getJSONArray("parts").getJSONObject(0).getString("text");

                JSONObject formattedMessage = new JSONObject();
                formattedMessage.put("role", role);
                JSONArray formattedParts = new JSONArray();
                formattedParts.put(new JSONObject().put("text", text));
                formattedMessage.put("parts", formattedParts);
                contents.put(formattedMessage);
            }

            jsonBody.put("contents", contents);

            RequestBody body = RequestBody.create(mediaType, jsonBody.toString());
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> speak(SPEAK_GEMINI_CONNECTION_ERROR));
                    Log.e("GeminiAPI", "API isteği başarısız oldu: " + e.getMessage());
                    if (!chatHistory.isEmpty()) {
                        chatHistory.remove(chatHistory.size() - 1);
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            JSONObject resp = new JSONObject(responseBody);
                            JSONArray candidates = resp.optJSONArray("candidates");
                            String geminiReply = "";

                            if (candidates != null && candidates.length() > 0) {
                                JSONObject firstCandidate = candidates.getJSONObject(0);
                                JSONObject contentObj = firstCandidate.optJSONObject("content");
                                if (contentObj != null) {
                                    JSONArray parts = contentObj.optJSONArray("parts");
                                    if (parts != null && parts.length() > 0) {
                                        geminiReply = parts.getJSONObject(0).optString("text", "").trim().toLowerCase(Locale.ROOT);

                                        // Modelin yanıtını chatHistory'ye ekle
                                        JSONObject newModelTurn = new JSONObject();
                                        newModelTurn.put("role", "model");
                                        JSONArray newModelParts = new JSONArray();
                                        newModelParts.put(new JSONObject().put("text", geminiReply));
                                        newModelTurn.put("parts", newModelParts);
                                        chatHistory.add(newModelTurn);

                                    }
                                }
                            }

                            final String finalGeminiReply = geminiReply;
                            runOnUiThread(() -> processCommand(finalGeminiReply));

                        } catch (JSONException e) {
                            runOnUiThread(() -> speak(SPEAK_GEMINI_RESPONSE_ERROR));
                            Log.e("GeminiAPI", "JSON ayrıştırma hatası: " + e.getMessage());
                            if (!chatHistory.isEmpty()) {
                                chatHistory.remove(chatHistory.size() - 1);
                            }
                        }
                    } else {
                        runOnUiThread(() -> speak(SPEAK_GEMINI_NO_RESPONSE));
                        Log.e("GeminiAPI", "Başarısız API yanıtı: " + response.code() + " " + response.message());
                        if (!chatHistory.isEmpty()) {
                            chatHistory.remove(chatHistory.size() - 1);
                        }
                    }
                }
            });
        } catch (JSONException e) {
            speak(SPEAK_GEMINI_REQUEST_FAIL);
            Log.e("GeminiAPI", "JSON oluşturma hatası: " + e.getMessage());
        }
    }

    /**
     * Gemini'den gelen komutları işler ve WebView ile etkileşim kurar.
     * Bu metot, her komutun sadece bir kez işlenmesini ve sadece bilinen komutlara yanıt verilmesini sağlar.
     *
     * @param command Gemini'den gelen normalize edilmiş komut dizisi.
     */
    private void processCommand(String command) {
        String normCommand = command.trim();
        boolean spoken = false; // speak() metodunun çağrılıp çağrılmadığını takip etmek için

        if (normCommand.isEmpty()) {
            Log.d("ProcessCommand", "Boş komut algılandı, işlem yapılmıyor. Dinleme devam ettiriliyor.");
            // Boş komut gelse bile dinlemeyi tekrar başlat.
            // Bu durumda speak() çağrılmadığı için manuel olarak başlatmalıyız.
            if (isListening) {
                handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
            }
            return;
        }

        // Komutlara göre işlem yap ve sesli geri bildirim ver
        if (normCommand.equals(COMMAND_ILERI)) {
            clickButtonJS("ileriButton");
            speak(SPEAK_GOING_FORWARD);
            spoken = true;
        } else if (normCommand.equals(COMMAND_GERI)) {
            clickButtonJS("geriButton");
            speak(SPEAK_GOING_BACKWARD);
            spoken = true;
        } else if (normCommand.equals(COMMAND_SAG)) {
            clickButtonJS("sagButton");
            speak(SPEAK_TURNING_RIGHT);
            spoken = true;
        } else if (normCommand.equals(COMMAND_SOL)) {
            clickButtonJS("solButton");
            speak(SPEAK_TURNING_LEFT);
            spoken = true;
        } else if (normCommand.equals(COMMAND_DUR)) {
            clickButtonJS("stopButton");
            speak(SPEAK_STOPPING);
            spoken = true;
        } else if (normCommand.equals(COMMAND_TURBO_AC)) {
            setCheckboxJS("turboCheckbox", true);
            speak(SPEAK_TURBO_ON);
            spoken = true;
        } else if (normCommand.equals(COMMAND_TURBO_KAPAT)) {
            setCheckboxJS("turboCheckbox", false);
            speak(SPEAK_TURBO_OFF);
            spoken = true;
        } else if (normCommand.equals(COMMAND_SU_FISKIRT)) {
            clickButtonJS("suPompasibuton");
            speak(SPEAK_WATER_SPRAYING);
            spoken = true;
        }
        // Eğer prompt'unuza "su durdur" komutunu eklediyseniz, bu bloğu aktif edin:
        /*
        else if (normCommand.equals(COMMAND_SU_DURDUR)) {
            clickButtonJS("suPompasinibutton"); // HTML'deki ilgili buton ID'sini kontrol edin
            speak(SPEAK_WATER_STOPPED);
            spoken = true;
        }
        */
        else if (normCommand.equals(COMMAND_SERVO_SIFIRLA)) {
            clickButtonJS("servoResetButton");
            speak(SPEAK_SERVO_RESET);
            spoken = true;
        } else if (normCommand.startsWith("servoyu ") && normCommand.endsWith(" derece yap")) {
            // "servoyu X derece yap" komutunu yakala
            Pattern servoPattern = Pattern.compile("servoyu\\s*([+-]?\\d{1,3})\\s*derece\\s*yap");
            Matcher matcher = servoPattern.matcher(normCommand);
            if (matcher.find()) {
                try {
                    int degree = Integer.parseInt(matcher.group(1));

                    // Servo yuvarlama mantığı (Prompt'taki kurallara göre)
                    int[] allowed = {-90, -60, -30, 0, 30, 60, 90};
                    int finalDegree = 0;

                    // Aralık dışı kontrolü
                    if (degree < -90) {
                        finalDegree = -90;
                    } else if (degree > 90) {
                        finalDegree = 90;
                    } else {
                        // En yakın değeri bulma
                        int minDiff = Integer.MAX_VALUE;
                        for (int val : allowed) {
                            int diff = Math.abs(val - degree);
                            if (diff < minDiff) {
                                minDiff = diff;
                                finalDegree = val;
                            } else if (diff == minDiff) {
                                // Aynı fark varsa, pozitif yöndeki en yakın değeri tercih et
                                // veya prompt'unuzdaki örneklerde olduğu gibi davranın.
                                // Prompt örneği 45 için 60'ı tercih etti, bu durumda bu mantık iyi.
                                if (val > finalDegree) {
                                    finalDegree = val;
                                }
                            }
                        }
                    }
                    setSliderJS("servoSlider", finalDegree + 90); // Slider 0-180 arası, merkezi 90
                    speak("Servo " + finalDegree + " dereceye ayarlandı.");
                    spoken = true;
                } catch (NumberFormatException e) {
                    Log.e("ProcessCommand", "Servo derecesi format hatası: " + normCommand);
                }
            }
        }

        // Eğer hiçbir komut işlenmediyse ve "speak" çağrılmadıysa, dinlemeyi tekrar başlat
        if (!spoken && isListening) {
            Log.d("ProcessCommand", "Tanınmayan veya boş yanıt alındı. Dinlemeye devam ediliyor.");
            handler.postDelayed(MainActivity.this::startListening, SHORT_DELAY_MS);
        }
    }


    private void clickButtonJS(String id) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("var btn = document.getElementById('" + id + "'); if(btn) { btn.click(); }", null);
                Log.d("WebViewJS", "Executing JS: Click button " + id);
            }
        });
    }

    private void setCheckboxJS(String id, boolean checked) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("var cb=document.getElementById('" + id + "'); if(cb) { cb.checked=" + checked + "; cb.dispatchEvent(new Event('change')); }", null);
                Log.d("WebViewJS", "Executing JS: Set checkbox " + id + " to " + checked);
            }
        });
    }


    private void setSliderJS(String id, int value) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("var s=document.getElementById('" + id + "'); if(s) { s.value=" + value + "; s.dispatchEvent(new Event('input')); s.dispatchEvent(new Event('change')); }", null);
                Log.d("WebViewJS", "Executing JS: Set slider " + id + " to " + value);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.shutdown();
        }
        stopListenTimeout();
        handler.removeCallbacksAndMessages(null);
    }
}
