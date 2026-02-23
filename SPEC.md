Bu dosya Mikro-Yetenek Danışmanı sisteminin bağlayıcı teknik spesifikasyonudur.
Kod bu belgeye %100 uymalıdır.
Kod bu belgeye aykırı mantık içeremez.

════════════════════════════════════════════════════════════════════
MİKRO-YETENEK DANIŞMANI SİSTEMİ
DETAYLI TÜRKÇE AÇIKLAMA
════════════════════════════════════════════════════════════════════

Bu dokümanda sistemin tamamını Türkçe olarak detaylıca açıklıyorum.

════════════════════════════════════════════════════════════════════
BÖLÜM 1: SİSTEM NEDİR VE NE İŞE YARAR?
════════════════════════════════════════════════════════════════════

Bu sistem, insanların becerilerini ölçen bir online quiz (sınav) sistemidir.

NORMAL QUİZ'LERDEN FARKI:
Normal quiz: "İletişim becerilerinizi 1-5 arasında puanlayın"
Bu sistem: "Birinin sizi anlamadığı bir durumu anlatın, ne yaptınız?"
→ AI cevabınızı okur ve hangi becerileri gösterdiğinizi
kendisi tespit eder

Kullanıcı sorulara AÇIK UÇLU YAZI ile cevap verir.
Sistem her cevabı AI'ya gönderir.
AI cevabı analiz eder ve hangi becerilerin mevcut olduğunu saptar.
Sonunda kullanıcı kendi beceri profilini görür.

ÖNEMLİ: Kullanıcı neyin ölçüldüğünü bilmez. Bu yüzden kandıramaz.
Sorular nötr, beceri isimleri sadece sistemin içindedir.

════════════════════════════════════════════════════════════════════
BÖLÜM 2: SİSTEM MİMARİSİ (ÇOK ÖNEMLİ)
════════════════════════════════════════════════════════════════════

Sistem İKİ PARÇADAN oluşur:

┌────────────────────────────────────────────────────────────────┐
│ PARÇA 1 — KOD (Senin yazacağın)                                │
│                                                                │
│ Kod SADECE şunları yapar:                                      │
│   • Soruları kullanıcıya gösterir                              │
│   • Text cevapları toplar                                      │
│   • Her cevabı AI'ya API ile gönderir                          │
│   • AI'nın döndürdüğünü saklar                                 │
│   • Puan eşiklerine ulaşıldı mı diye kontrol eder              │
│   • AI'nın ürettiği sonuçları gösterir                         │
│                                                                │
│ Kod ASLA şunları yapmaz:                                       │
│   • Puan hesaplama                                             │
│   • Kategori kararı                                            │
│   • Cevap kalitesini değerlendirme                             │
│   • Sonuç formatını şekillendirme                              │
│   • Beceri mantığı içermez                                     │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│ PARÇA 2 — AI (API ile çağrılır)                                │
│                                                                │
│ AI TÜM ZEKÂYI yapar:                                           │
│   • Her cevabı okur                                            │
│   • Hangi becerilerin mevcut olduğunu tespit eder              │
│   • Puan değerleri atar (1, 2, 3, 5, 8, veya 13)              │
│   • Cevabın gerçek mi spam mi olduğunu kontrol eder            │
│   • Sonda: kategoriler, puanlar, açıklamalar oluşturur         │
└────────────────────────────────────────────────────────────────┘

ÇOK ÖNEMLİ KURAL: Kod aptal bir kabuk. AI beyindir.
Bu tasarım tüm mantığı AI prompt'larında tutar, esnektir.

════════════════════════════════════════════════════════════════════
BÖLÜM 3: KULLANICI AKIŞI (BAŞTAN SONA)
════════════════════════════════════════════════════════════════════

ADIM 1 — Quiz Başlar
→ Sistem bellekte (memory) bir master JSON oluşturur
→ 77 beceri anahtarı hepsi 0'a set edilir
→ Bir özel "INVALID" anahtarı da 0'a set edilir

ADIM 2 — Soru Göster
→ questions.json dosyasından sıradaki soruyu yükle
→ Soru metnini göster
→ Text input kutusu göster
→ İki buton göster: [İleri] ve [Atla]

ADIM 3 — Kullanıcı Cevap Verir
→ Kullanıcı cevabını yazar
→ [İleri] butonuna basar
→ HEMEN soru + cevap AI'ya gönderilir (PROMPT A ile)
→ BEKLEME. Bir sonraki soruyu hemen göster.
→ AI çağrısı arka planda asenkron olarak çalışır

ADIM 4 — AI Puanlama JSON'u Döndürür
→ AI cevabı analiz etti
→ Şunu döner: hangi beceriler tespit edildi, her biri kaç puan
→ Kod bu puanları master JSON'a ekler
→ Kod başka bir şey yapmaz

ADIM 5 — Checkpoint Kontrolü (Her 3 cevapta bir)
→ Kontrol: toplam puan >= sıradaki eşik mi?
→ Kontrol: minimum soru sayısı cevaplandı mı?
→ İkisi de EVET ise → checkpoint ekranını göster
→ Hayır ise → soru sormaya devam et

ADIM 6 — Checkpoint Ekranı (Eşiklerde gösterilir)
→ Kullanıcıya "Profiliniz hazır" mesajı
→ Profil kalite seviyesini göster
→ İki seçenek sun:
[Sonuçlarımı Gör] veya [Daha Fazla Detay İçin Devam Et]
→ Sonuçları Gör → Adım 7'ye git
→ Devam Et → Adım 2'ye dön

ADIM 7 — Sonuçlar Sayfası
→ Tüm master JSON'u (77 beceri puanı) AI'ya gönder (PROMPT B ile)
→ AI şunu döner: kategoriler, puanlar, açıklamalar, içgörüler
→ Kod AI'nın döndürdüğünü AYNEN gösterir
→ Kullanıcı nihai kişisel profilini görür

════════════════════════════════════════════════════════════════════
BÖLÜM 4: PUANLAMA SİSTEMİ (NASIL ÇALIŞIR)
════════════════════════════════════════════════════════════════════

77 MİKRO-BECERİ

Bunlar sistemin içinde ölçtüğü beceriler:

* deductive_reasoning (tümdengelim mantığı)
* empathy (empati)
* planning (planlama)
* creativity (yaratıcılık)
* vs. (toplam 77 tane)

Kullanıcı bu isimleri ASLA görmez.
Bunlar sadece iç ölçüm birimleridir.

FİBONACCİ PUANLAMA ÖLÇEĞİ

AI sadece bu değerleri kullanarak puan verir:

1  = İz (çok hafif bahsedilmiş veya ima edilmiş)
2  = Zayıf (bahsedilmiş ama gösterilmemiş)
3  = Fark Edilir (mevcut ve görünür)
5  = Net (aktif olarak gösterilmiş)
8  = Güçlü (belirgin şekilde gösterilmiş)
13 = İstisnai (baskın tema, çoklu örnek, sofistike)

Bir cevap birden fazla beceriye farklı seviyelerde puan verebilir.

ÖRNEK:
Bir cevap şunları alabilir:
empathy: 8 puan
active_listening: 5 puan
self_awareness: 3 puan
Toplam: 16 puan bu cevaptan

CHECKPOINT EŞİKLERİ (İlerici Durak Noktaları)

CP1: 280 puan + minimum 10 soru cevaplandı → "Temel profil"
CP2: 420 puan + minimum 15 soru cevaplandı → "İyi profil"
CP3: 560 puan + minimum 20 soru cevaplandı → "Çok detaylı"
CP4: 700 puan + minimum 25 soru cevaplandı → "Maksimum derinlik"

Sistem her 3 cevaplanan soruda bir kontrol eder.
Kullanıcı herhangi bir checkpoint'te durabilir veya devam edebilir.

45 SORU, 3 KADEMELİ YAPI

Kademe 1 (S1-S15):  Kritik, önce sorulmalı, tüm domainleri kapsar
Kademe 2 (S16-S30): Derinlik ve yedeklilik, CP1'den sonra sorulur
Kademe 3 (S31-S45): İnce ayar, CP2'den sonra sorulur

════════════════════════════════════════════════════════════════════
BÖLÜM 5: MASTER SESSION JSON (VERİ YAPISI)
════════════════════════════════════════════════════════════════════

Quiz başladığında oluşturulur, quiz boyunca bellekte (memory) yaşar.
Tüm AI cevapları buraya kaydedilir.
Kod veritabanına SADECE BİR KEZ yazar, quiz bittiğinde.

{
"sessionId": "unique-id",
"startedAt": "timestamp",

"counters": {
"questionsShown": 0,      // Kaç soru gösterildi
"questionsAnswered": 0,   // Kaç tanesi gerçekten cevaplandı
"questionsSkipped": 0,    // Kaç tanesi atlandı (Skip)
"invalidAnswers": 0       // Kaç tanesi AI tarafından spam olarak işaretlendi
},

"points": {
"total": 0                // Tüm cevaplardan gelen puanların toplamı
},

"checkpoints": {
"reached": 0              // Ulaşılan en yüksek checkpoint (0-4)
},

"microSkillScores": {
// Tüm 77 beceri 0'dan başlar, cevaplar puanlandıkça birikir
"deductive_reasoning": 0,
"inductive_reasoning": 0,
"logical_reasoning": 0,
"critical_thinking": 0,
"pattern_recognition": 0,
"problem_identification": 0,
"analytical_problem_solving": 0,
"quantitative_reasoning": 0,
"abstract_thinking": 0,
"big_picture_thinking": 0,
"information_structuring": 0,
"cognitive_flexibility": 0,
"learning_agility": 0,
"judgment": 0,
"fluency_of_ideas": 0,
"originality": 0,
"divergent_thinking": 0,
"imagination": 0,
"visual_thinking": 0,
"associative_thinking": 0,
"experimental_mindset": 0,
"clarity_of_expression": 0,
"conciseness": 0,
"persuasive_communication": 0,
"active_listening": 0,
"storytelling": 0,
"simplifying_complexity": 0,
"constructive_feedback": 0,
"audience_adaptation": 0,
"teamwork": 0,
"leadership": 0,
"empathy": 0,
"conflict_resolution": 0,
"negotiation": 0,
"instructing": 0,
"mentoring": 0,
"service_orientation": 0,
"social_perceptiveness": 0,
"delegation": 0,
"networking": 0,
"cultural_sensitivity": 0,
"self_control": 0,
"stress_tolerance": 0,
"resilience": 0,
"self_awareness": 0,
"positive_attitude": 0,
"patience": 0,
"self_confidence": 0,
"self_reflection": 0,
"planning": 0,
"time_management": 0,
"prioritization": 0,
"organization": 0,
"attention_to_detail": 0,
"methodical_approach": 0,
"dependability": 0,
"perseverance": 0,
"efficiency": 0,
"multitasking": 0,
"focused_concentration": 0,
"quality_focus": 0,
"decisiveness": 0,
"achievement_orientation": 0,
"initiative": 0,
"independence": 0,
"self_motivation": 0,
"self_learning": 0,
"accountability": 0,
"resourcefulness": 0,
"intellectual_curiosity": 0,
"adaptability": 0,
"ambiguity_tolerance": 0,
"risk_management": 0,
"routine_tolerance": 0,
"rule_adherence": 0,
"INVALID": 0              // Spam takibi için özel anahtar
},

"answers": []               // Tüm cevapların metadata'sı ile array
}

════════════════════════════════════════════════════════════════════
BÖLÜM 6: İKİ AI PROMPT'U
════════════════════════════════════════════════════════════════════

Sistemde TAM OLARAK İKİ AI API çağrısı vardır:

┌────────────────────────────────────────────────────────────────┐
│ PROMPT A — PUANLAMA PROMPT'U (scoring.txt)                     │
│ Ne zaman: HER cevaptan sonra                                   │
│ Sıklık: Oturum başına 10-27 kere (kullanıcıya bağlı)          │
│                                                                │
│ AI'ya gönderilen:                                              │
│   - Soru metni                                                 │
│   - Kullanıcının cevap metni                                   │
│   - Beklenen puan aralığı (sağlık kontrolü için)              │
│                                                                │
│ AI döner:                                                      │
│   {                                                            │
│     "response_type": "valid" | "skipped" | "invalid",         │
│     "total_points": sayı,                                      │
│     "skills_detected": [                                       │
│       { "skill_id": "empathy", "points": 8 }                   │
│     ]                                                          │
│   }                                                            │
│                                                                │
│ Kod yapar:                                                     │
│   - Her becerinin puanını microSkillScores'a ekler            │
│   - total_points'i points.total'e ekler                        │
│   - İlgili sayacı artırır                                      │
│   - Başka hiçbir şey yapmaz                                    │
│                                                                │
│ scoring.txt (SİSTEM PROMPT) İÇERİĞİ:                           │
└────────────────────────────────────────────────────────────────┘

```text
SENİN ROLÜN:
Sen, açık uçlu bir cevaptan (USER ANSWER) kanıta dayalı mikro-beceri tespiti yapan bir "scoring engine"sin.
Görevin: cevapta GERÇEKTEN görülen becerileri seçmek ve sadece Fibonacci ölçeğiyle puanlamak.

GİRİŞ FORMATI (KULLANICI MESAJI İÇİNDE GELECEK):
QUESTION: ...
EXPECTED POINTS: minimum X / target Y / excellent Z

USER ANSWER:
...

ÇIKTI KURALI (ÇOK KRİTİK):
SADECE ve SADECE aşağıdaki JSON'u döndür. Başka metin, açıklama, markdown, kod bloğu yok.
JSON şeması:
{
  "response_type": "valid" | "skipped" | "invalid",
  "total_points": integer,
  "skills_detected": [
    { "skill_id": string, "points": integer }
  ]
}

PUANLAMA ÖLÇEĞİ (SADECE BU DEĞERLER):
1, 2, 3, 5, 8, 13

- 1  = İz (çok hafif ima / tek kelime / yüzeysel)
- 2  = Zayıf (bahsedilmiş ama davranışla gösterilmemiş)
- 3  = Fark edilir (davranış olarak görülüyor)
- 5  = Net (aktif biçimde uygulanmış)
- 8  = Güçlü (birden fazla işaret / belirgin ve etkili)
- 13 = İstisnai (baskın tema + çoklu örnek + sofistike)

ALLOWED_SKILL_IDS (SADECE BUNLAR):
deductive_reasoning, inductive_reasoning, logical_reasoning, critical_thinking, pattern_recognition,
problem_identification, analytical_problem_solving, quantitative_reasoning, abstract_thinking,
big_picture_thinking, information_structuring, cognitive_flexibility, learning_agility, judgment,
fluency_of_ideas, originality, divergent_thinking, imagination, visual_thinking, associative_thinking,
experimental_mindset, clarity_of_expression, conciseness, persuasive_communication, active_listening,
storytelling, simplifying_complexity, constructive_feedback, audience_adaptation, teamwork, leadership,
empathy, conflict_resolution, negotiation, instructing, mentoring, service_orientation,
social_perceptiveness, delegation, networking, cultural_sensitivity, self_control, stress_tolerance,
resilience, self_awareness, positive_attitude, patience, self_confidence, self_reflection, planning,
time_management, prioritization, organization, attention_to_detail, methodical_approach, dependability,
perseverance, efficiency, multitasking, focused_concentration, quality_focus, decisiveness,
achievement_orientation, initiative, independence, self_motivation, self_learning, accountability,
resourcefulness, intellectual_curiosity, adaptability, ambiguity_tolerance, risk_management,
routine_tolerance, rule_adherence

NOT:
- "INVALID" skill_id olarak ASLA yazma. INVALID sadece kodun sayaç anahtarıdır.
- skills_detected listesine ALLOWED_SKILL_IDS dışında bir şey ekleme.

response_type KARARI:
1) "skipped"
- USER ANSWER boşsa veya yalnızca "skip", "pas", "bilmiyorum", "geç" gibi net atlama niyeti varsa
- Bu durumda: total_points = 0, skills_detected = []

2) "invalid"
Aşağıdakilerden biri varsa:
- Cevap anlamsız/gürültü: rastgele harfler, tek emoji, tekrar, spam, reklam, kopya-metin
- Soruyla ilgisiz (cevap genel/gevezelik ama soruya temas yok)
- Sadece iddia: "Ben çok iyiyim" gibi kanıt içermeyen kendini övme
- Çok kısa/boş içerik (anlamlı örnek yok, bağlam yok)
Bu durumda: total_points = 0, skills_detected = []

3) "valid"
- Cevap soruya makul şekilde temas ediyor ve en az bir davranış/örnek/aksiyon içeriyor.

BECERİ TESPİTİ KURALLARI:
- Sadece "kanıt" varsa beceri ekle: davranış, karar, yaklaşım, adım, örnek.
- Aşırı etiketleme yapma: maksimum 8 beceri seç (genelde 3–6 ideal).
- skills_detected içinde her skill_id en fazla 1 kez geçsin.
- points, sadece Fibonacci değerlerinden biri olmalı.
- Becerileri puana göre azalan sırada listele (en yüksek puan üstte).
- total_points = skills_detected içindeki puanların toplamı (integer).

EXPECTED POINTS (sağlık kontrolü):
- Minimum/target/excellent değerleri bir "beklenti bandı"dır.
- total_points aşırı uç görünüyorsa (çok düşük ya da çok yüksek), önce becerileri tekrar gözden geçir:
  * Kanıt yoksa puan kır, becerileri azalt.
  * Kanıt çok güçlü değilse 13 verme.
- Yine de cevap gerçekten çok güçlüyse yüksek puan vermekten kaçınma; ama kanıt şart.

GİZLİLİK / FORMAT:
- Kullanıcıya beceri isimlerini açıklama.
- Yorum, gerekçe, ek alan ekleme.
- Sadece JSON döndür.
```

┌────────────────────────────────────────────────────────────────┐
│ PROMPT B — SONUÇ OLUŞTURMA PROMPT'U (results.txt)              │
│ Ne zaman: Quiz bittiğinde bir kere                             │
│ Sıklık: Oturum başına tam olarak 1 kere                       │
│                                                                │
│ AI'ya gönderilen:                                              │
│   - Tüm 77 microSkillScores                                    │
│   - Session sayaçları (cevaplanan, atlanan, vs)               │
│   - Ulaşılan checkpoint                                        │
│                                                                │
│ AI döner:                                                      │
│   {                                                            │
│     "profile_quality": "Basic" | "Good" | vb,                 │
│     "overall_summary": "2-3 cümle özet",                      │
│     "categories": [                                            │
│       {                                                        │
│         "name": "Analitik Düşünme",                            │
│         "score": 68,                                           │
│         "label": "Good",                                       │
│         "explanation": "..."                                   │
│       }                                                        │
│     ],                                                         │
│     "strongest_areas": [...],                                  │
│     "growth_areas": [...]                                      │
│   }                                                            │
│                                                                │
│ Kod yapar:                                                     │
│   - Bu JSON'u AYNEN gösterir                                   │
│   - Düzenleme, filtreleme, hesaplama yapmaz                    │
│   - AI kategori isimlerini bile kendisi karar verir            │
│                                                                │
│ results.txt (SİSTEM PROMPT) İÇERİĞİ:                           │
└────────────────────────────────────────────────────────────────┘

```text
SENİN ROLÜN:
Sen, bir kullanıcının quiz oturumunda biriken mikro-beceri puanlarından kişiselleştirilmiş bir "beceri profili raporu" üreten analiz motorusun.
Girdi olarak sadece skorlar ve sayaçlar gelir; sen kategorileri, skorları, açıklamaları ve içgörüleri üretirsin.

GİRİŞ (KULLANICI MESAJI İÇİNDE GELECEK):
- QUESTIONS ANSWERED, QUESTIONS SKIPPED, INVALID ANSWERS, TOTAL POINTS, CHECKPOINT REACHED
- MICRO-SKILL SCORES: { ... } (77 skill_id anahtarı ve integer puanlar)

ÇIKTI KURALI (ÇOK KRİTİK):
SADECE ve SADECE aşağıdaki JSON'u döndür. Başka metin, açıklama, markdown, kod bloğu yok.
JSON şeması:
{
  "profile_quality": "Basic" | "Good" | "Very Detailed" | "Maximum",
  "overall_summary": "2-3 cümle",
  "categories": [
    { "name": string, "score": integer, "label": string, "explanation": string }
  ],
  "strongest_areas": [
    { "skill_name": string, "reason": string }
  ],
  "growth_areas": [
    { "skill_name": string, "reason": string }
  ]
}

GENEL İLKELER:
- Mikro-beceri ID'lerini kullanıcıya GÖSTERME (ör. empathy yazma). Bunun yerine doğal Türkçe beceri isimleri kullan (ör. "Empati").
- "overall_summary" 2-3 cümle olmalı, net ve profesyonel bir tonda.
- Bu bir psikolojik/klinik değerlendirme değildir; teşhis dili kullanma.
- "Kesin" iddialardan kaçın; "cevaplarınıza göre", "gözlenen" gibi temkinli ifade kullan.

profile_quality KARARI:
- CHECKPOINT REACHED ve QUESTIONS ANSWERED'ı birlikte kullan:
  * 0 veya çok düşük veri: "Basic"
  * CP1 civarı: "Basic"
  * CP2 civarı: "Good"
  * CP3 civarı: "Very Detailed"
  * CP4 civarı: "Maximum"
- INVALID ANSWERS yüksekse (>=7) kaliteyi bir kademe düşür ve summary içinde kibar bir kalite notu ima et (aşırı sert olma).

CATEGORIES (KATEGORİ ÜRETİMİ):
- 4 ile 7 arasında kategori üret.
- Kategoriler SABİT değil; kullanıcının yüksek skor kümelerine göre karar ver.
- Her kategori için:
  * name: Türkçe ve anlaşılır (örn. "Analitik Düşünme", "İletişim ve İnsan Becerileri", "Öz Yönetim", "Yaratıcı Problem Çözme", "Uyum ve Esneklik" vb.)
  * score: 0-100 arası integer. Kullanıcının ilgili mikro-skor yoğunluğuna göre normalize et.
  * label: tek kelimelik seviye etiketi kullan: "Weak" | "Basic" | "Good" | "Strong" | "Exceptional"
  * explanation: 2-4 cümle Türkçe açıklama; güçlü kanıt temaları ve tipik davranış desenleri.

SKORLAMA / NORMALİZASYON KURALI (basit ve tutarlı):
- Mutlak puanlar kişiden kişiye değişebilir; bu yüzden "oran/yoğunluk" yaklaşımı kullan.
- total_points ve dağılıma göre kategorileri birbirine göre 0-100 ölçeğine oturt:
  * En güçlü kategori genelde 70-90 bandında
  * Orta kategoriler 45-70 bandında
  * Zayıf kategoriler 25-45 bandında
- Aşırı uç üretme (herkese 95 verme ya da herkesi 20'de bırakma).

STRONGEST_AREAS:
- 3 ile 5 madde üret.
- "skill_name" kullanıcı diliyle beceri adı olsun.
- "reason" 1-2 cümle; hangi tür cevap/tema üzerinden güçlü göründüğünü anlat (mikro-skill id kullanmadan).

GROWTH_AREAS:
- 3 ile 5 madde üret.
- Bu alanlar "zayıflık" değil; "daha az görünmüş / fırsat bulmamış" şeklinde yapıcı dille yaz.
- "reason" 1-2 cümle; geliştirme önerisi gibi değil, gözlem temelli eksik görünüm gibi ifade et.

FORMAT / KISIT:
- Tüm sayılar integer olmalı.
- Alan isimlerini aynen koru.
- Ek alan ekleme.
- Sadece JSON döndür.
```

ÖNEMLİ: Kategoriler SABİT DEĞİL. AI her kullanıcının gerçek
puanlarına göre onları karar verir. Farklı kullanıcılar farklı
kategoriler görür.

════════════════════════════════════════════════════════════════════
BÖLÜM 7: ÖZEL KURALLAR VE DURUM KONTROLÜ
════════════════════════════════════════════════════════════════════

ATLAMA (SKIP) İŞLEMLERİ

* Kullanıcı herhangi bir soruyu atlayabilir (buton her zaman görünür)
* Maksimum 7 atlama izin verilir
* 7 atlamadan sonra: skip butonunu gizle
* Atlanan sorular checkpoint kontrolüne SAYILMAZ
* Atlanan sorular puan eklemez
* Atlanan sorular için AI çağrılmaz

GEÇERSİZ (INVALID) CEVAP İŞLEMİ

* AI "response_type": "invalid" dönerse
* INVALID sayacını artır
* questionsAnswered'ı artır (kullanıcı denedi)
* 0 puan ekle
* INVALID >= 4 ise: kullanıcıya yumuşak uyarı göster
* INVALID >= 7 ise: kullanıcıya sert uyarı göster

CHECKPOINT KONTROL ZAMANLAMASI

* SADECE her 3 CEVAPLANAN soruda kontrol et
* Atlanan sorular kontrolü tetiklemez
* Geçersiz cevaplar cevaplandı sayılır (kullanıcı denedi)
* Zaten geçilmiş checkpoint'i asla tekrar kontrol etme

SERT LİMİTLER

* Maksimum 35 soru gösterilebilir (atlamalar dahil)
* 35'e ulaşıldı ve checkpoint yok ise:
  yine de sonuçları göster, kalite uyarısı ile
* Minimum cevap uzunluğu: 10 karakter
  (daha az ise, kullanıcıdan daha fazla yazmasını iste, ilerletme)

AI ÇAĞRISI BAŞARISIZ OLURSA

* AI çağrısı başarısız olursa: otomatik 1 kere tekrar dene
* Hala başarısız ise: o cevabı puanlama, quiz'e devam et
* Hatayı sessizce logla, kullanıcıyı bloklama

════════════════════════════════════════════════════════════════════
BÖLÜM 8: SORU SEÇİM MANTIĞI
════════════════════════════════════════════════════════════════════

Sorular questions.json içinde 3 kademede organize:

Kademe 1 (S1-S15):  Her zaman önce bunları sor, sırayla
Kademe 2 (S16-S30): Kullanıcı CP1'e ulaşıp devam ederse sor
Kademe 3 (S31-S45): Kullanıcı CP2'ye ulaşıp devam ederse sor

SEÇİM KURALLARI:

1. Kademe 1'in tamamını bitir, Kademe 2'ye geçmeden önce
2. Kademe 2'nin tamamını bitir, Kademe 3'e geçmeden önce
3. Her zaman kademe içinde sırayı koru (S1, S2, S3...)
4. İstisna: Bir soru atlanırsa ve backup_question_id varsa,
   o yedek soruyu mevcut kuyruğun önüne çek

════════════════════════════════════════════════════════════════════
BÖLÜM 9: KULLANICI ARAYÜZÜ GEREKSİNİMLERİ
════════════════════════════════════════════════════════════════════

QUİZ SAYFASI:

┌──────────────────────────────────────────┐
│ Soru 7                                   │
├──────────────────────────────────────────┤
│                                          │
│ [Soru metni burada]                      │
│                                          │
│ ┌────────────────────────────────────┐   │
│ │ [Büyük text input, min 4 satır]    │   │
│ │                                    │   │
│ └────────────────────────────────────┘   │
│                                          │
│ [İleri →]  [Bu soruyu atla]              │
└──────────────────────────────────────────┘

* İlerleme barı YOK (toplam soru sayısını gizler)
* AI çağrısı arka planda, kullanıcıyı bloklama
* Cevap < 10 karakter ise: mesaj göster, ilerletme

CHECKPOINT EKRANI:

┌──────────────────────────────────────────┐
│ ✓ Kontrol Noktasına Ulaşıldı             │
│                                          │
│ Profil Kalitesi: İyi                     │
│ Cevaplanan Soru: 15                      │
│                                          │
│ Sonuçlarınızı şimdi görebilirsiniz veya  │
│ daha fazla detay için devam edebilirsiniz│
│ (~5 soru daha).                          │
│                                          │
│ [Sonuçlarımı Gör]  [Devam Et]            │
└──────────────────────────────────────────┘

SONUÇLAR SAYFASI:

* PROMPT B işlerken yükleniyor göstergesi göster
* AI'nın dönüş değerini AYNEN göster:
  • overall_summary üstte
  • categories yatay barlar olarak (puan = genişlik)
  • strongest_areas bölüm
  • growth_areas bölüm
* AI çıktısını düzenleme veya yeniden formatla

════════════════════════════════════════════════════════════════════
BÖLÜM 10: DOSYA YAPISI
════════════════════════════════════════════════════════════════════

/data/
questions.json      ← Metadata ile 45 soru
microskills.json    ← İsim ve domain ile 77 beceri

/prompts/
scoring.txt         ← PROMPT A (cevapları puanlama)
results.txt         ← PROMPT B (final profil oluşturma)

/backend/
session.js          ← Session JSON oluştur ve yönet
api.js              ← AI API çağrılarını yönet
checkpoint.js       ← Eşik mantığını kontrol et

/frontend/
quiz.js             ← Quiz sayfası UI
results.js          ← Sonuçlar sayfası UI

════════════════════════════════════════════════════════════════════
BÖLÜM 11: ÖNEMLİ SAYILAR HIZLI REFERANS
════════════════════════════════════════════════════════════════════

Sorular:                    45 toplam (3 kademe, her biri 15)
Mikro-beceriler:            77 iç ölçüm birimi
Puanlama ölçeği:            1, 2, 3, 5, 8, 13 (Fibonacci)
Checkpoint'ler:             CP1=280, CP2=420, CP3=560, CP4=700
Minimum sorular:            10, 15, 20, 25 per checkpoint
Kontrol sıklığı:            Her 3 cevaplanan soruda
Oturum başına AI çağrısı:   11-28 (kullanıcıya bağlı)
Maks atlama:                7
Maks gösterilen soru:       35
Min cevap uzunluğu:         10 karakter

════════════════════════════════════════════════════════════════════
BÖLÜM 12: SİSTEM NE BAŞARIYOR?
════════════════════════════════════════════════════════════════════

AVANTAJ 1 — Tespit Edilemeyen Ölçüm
Kullanıcılar sistemi kandıramaz çünkü sorular nötr.
Neyin ölçüldüğünü bilmezler.

AVANTAJ 2 — Pasif Beceri Tespiti
Beceriler doğal yazma ve düşünme tarzından çıkarsanır,
kendi bildirilen iddialardan değil.

AVANTAJ 3 — Esnek Zeka
Tüm mantık AI prompt'larında yaşar, kodda değil.
Tespiti geliştirmek kolay, koda dokunmadan.

AVANTAJ 4 — Kişiselleştirilmiş Çıktı
AI her kullanıcı için farklı kategoriler ve açıklamalar üretir
gerçek puanlarına göre, sabit şablondan değil.

AVANTAJ 5 — Adaptif Uzunluk
Kullanıcılar erken durabilir veya derine gidebilir.
Çoğu kullanıcı 15-18 soruda iyi sonuç alır.
Mükemmelliyetçiler maksimum derinlik için 27 soruya gidebilir.

════════════════════════════════════════════════════════════════════
BÖLÜM 14: ÖRNEK SENARYO (15 SORUYA CEVAP)
════════════════════════════════════════════════════════════════════

Kullanıcı 15 soruya cevap verdi. Ne olur?

HER CEVAPTAN SONRA:
AI puanlar: toplam ~20-30 puan/cevap
Kod session'a ekler

15. CEVAPTAN SONRA:
    Total puan: ~295 puan
    questionsAnswered: 15

CHECKPOINT KONTROLÜ:
15 % 3 === 0 ✓
15 >= 10 ✓
295 >= 280 ✓
checkpoints.reached < 1 ✓

→ CP1 ekranını göster

KULLANICI "SONUÇLARIMI GÖR" TIKLAR:

AI'ya gönderilir (PROMPT B):
77 beceri puanı
15 cevaplandı, 0 atlandı, 0 geçersiz
295 toplam puan
Checkpoint 1

AI döner:
{
"profile_quality": "Basic",
"overall_summary": "Bu kişi pratik problem çözme yetenekleri
ve güçlü kişilerarası farkındalık gösteriyor.
Cevaplar net iletişim becerileri ile metodik
bir yaklaşım sergiliyor.",
"categories": [
{
"name": "İletişim ve İnsan Becerileri",
"score": 68,
"label": "Good",
"explanation": "Empati ve iletişim tarzını uyarlama yeteneği
net şekilde öne çıkıyor. Farklı dinleyicilere
göre yaklaşımı ayarlama beceriniz etkili."
},
{
"name": "Analitik Düşünme",
"score": 64,
"label": "Good",
"explanation": "Problemleri sistematik olarak adımlara
ayırıyor ve bilgide pattern arıyorsunuz."
},
...
],
"strongest_areas": [
{
"skill_name": "Empati",
"reason": "Çatışma ve öğretim senaryolarında sürekli
gösterildi"
}
],
"growth_areas": [
{
"skill_name": "Sayısal Muhakeme",
"reason": "Sayı ve veri bazlı düşünme bu cevaplarda ortaya
çıkma fırsatı bulamadı"
}
]
}

KULLANICI GÖRÜR:
Profil Kalitesi: Temel

Bu kişi pratik problem çözme yetenekleri ve güçlü
kişilerarası farkındalık gösteriyor. Cevaplar net
iletişim becerileri ile metodik bir yaklaşım sergiliyor.

İletişim ve İnsan Becerileri     68%
████████████████████░░░░░░░  Good

Analitik Düşünme                 64%
████████████████░░░░░░░░░░  Good

[... diğer kategoriler]

En Güçlü Alanlar:
• Empati - Çatışma ve öğretim senaryolarında
sürekli gösterildi

Gelişim Alanları:
• Sayısal Muhakeme - Sayı ve veri bazlı düşünme
bu cevaplarda ortaya çıkma fırsatı bulamadı

════════════════════════════════════════════════════════════════════
BÖLÜM 15: SONU ÖZET
════════════════════════════════════════════════════════════════════

SİSTEMİN AMACI:
İnsanların becerilerini pasif olarak tespit etmek
Kendi bildirilen iddialara güvenmeden
Doğal yazma tarzından çıkarsamak

SİSTEMİN YAPISI:
Kod = Aptal kabuk (göster, topla, gönder, sakla, göster)
AI = Beyin (analiz et, tespit et, puanla, kategorileştir)

SİSTEMİN AKIŞI:
Kullanıcı yazar → AI puanlar → Kod biriktirir →
Eşik kontrolü → Checkpoint → Devam veya Sonuç →
AI sonuç oluşturur → Kullanıcı görür

SİSTEMİN GÜÇLERİ:
Oynanması zor
Kişiselleştirilmiş
Esnek ve değiştirilebilir
Adaptif uzunluk
Profesyonel çıktı

SİSTEMİ KULLANAN:
15 soru → Temel profil (5 dakika)
20 soru → İyi profil (7 dakika)
27 soru → Maksimum profil (10 dakika)

Bu sistemle kullanıcılar kendilerini daha iyi anlayacak ve
beceri profillerini profesyonel bir formatta görecekler.

════════════════════════════════════════════════════════════════════
DÖKÜMANIN SONU
════════════════════════════════════════════════════════════════════
