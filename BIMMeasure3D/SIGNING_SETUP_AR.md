# BIM Measure 3D — توقيع ثابت وتحديثات بدون حذف

## لماذا تحتاج هذه الخطوة مرة واحدة فقط؟

Android يقارن شهادة توقيع APK الجديد بالشهادة المستخدمة للنسخة المثبتة. لا يمكن استبدال إصدار Debug موقّع بمفتاح مختلف دون فقدان استمرارية التثبيت المعتادة. لا توجد طريقة آمنة لتجاوز هذا الفحص.

في الإصدارات القديمة بُني APK بمفتاح Debug داخل GitHub Actions. لم نحتفظ بمفتاح التوقيع الخاص به، ولذلك **قد يلزم إلغاء تثبيت نسخة Debug السابقة مرة أخيرة** بعد تصدير القياسات. من الإصدار الثابت الموقّع بنفس المفتاح فصاعدًا تصبح التحديثات تثبيتًا فوق النسخة الحالية دون حذف البيانات، شريطة رفع versionCode.

## إنشاء مفتاح توقيع خاص على جهاز Windows

1. تأكد من وجود JDK 21 (الأمر `keytool`).
2. في PowerShell من داخل مجلد المشروع شغّل:
   ```powershell
   ./scripts/GenerateSigningKey.ps1
   ```
3. اختر كلمة مرور قوية واحفظ ملف `BIMMeasure3D-signing.p12` في مكان آمن خارج مستودع GitHub واحتفظ بنسخة احتياطية مشفرة.
4. **لا ترفع المفتاح، ولا كلمة مروره، ولا نص Base64 إلى مستودع عام أو إلى المحادثة**.

## إضافة الأسرار إلى GitHub مرة واحدة

المستودع: `bimspecialist/bim-specialist`

من GitHub اختر **Settings → Secrets and variables → Actions → New repository secret**. أضف:

| Secret | القيمة |
| --- | --- |
| `BIM_ANDROID_KEYSTORE_BASE64` | محتويات ملف p12 بعد تحويلها إلى Base64 |
| `BIM_ANDROID_STORE_PASSWORD` | كلمة مرور مخزن المفاتيح |
| `BIM_ANDROID_KEY_PASSWORD` | كلمة مرور المفتاح (في PKCS12 غالبًا هي نفسها) |
| `BIM_ANDROID_KEY_ALIAS` | `bim-measure-3d` |
| `BIM_ANDROID_CERT_SHA256` | بصمة الشهادة SHA-256 الناتجة من `keytool -list -v` |

نسخ Base64 إلى الحافظة دون إظهاره على الشاشة:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\BIMMeasure3D-keys\BIMMeasure3D-signing.p12")) | Set-Clipboard
```

الصق القيمة في GitHub Secret. بعد انتهاء ذلك امسح الحافظة:

```powershell
Set-Clipboard -Value ""
```

تحقق من البصمة:

```powershell
keytool -list -v -keystore "$HOME\BIMMeasure3D-keys\BIMMeasure3D-signing.p12" -alias bim-measure-3d
```

## نشر الإصدار الثابت الأول

الفرع: `bim-measure-3d-android-build`. بعد ضبط الأسرار يجب تنفيذ Commit داخل مسار `BIMMeasure3D/` ويتضمن عنوانه بالضبط `[signed-release]`، أو تشغيل مهمة `BIM Measure 3D - Signed Android Release` يدويًا إذا كانت ظاهرة في صفحة Actions.

مثال عنوان Commit:

```text
[signed-release] Publish BIM Measure 3D 0.4.0 beta
```

سيجري GitHub Actions اختبارات وحدة، Android Lint، بناء Release، فحص شهادة التوقيع، نشر APK على GitHub Releases، وكتابة بيانات الإصدار والتحقق SHA-256 في `BIMMeasure3D/updates/latest.json`. لا يمكن لعملية الإصدار النجاح دون الأسرار الخمسة.

بعد تثبيت النسخة الموقّعة الأولى على الجوال، سيظهر خيار التحديث داخل التطبيق للإصدارات المنشورة اللاحقة. يُنزّل التطبيق APK عبر HTTPS ويتحقق من SHA-256، ثم يطلب تأكيد تثبيت Android. **التحديث ليس صامتًا**، لكنه لا يتطلب حذف التطبيق إذا تطابق التوقيع وارتفع versionCode.

## الإصدارات التالية

زد `versionCode` و`versionName` في `app/build.gradle.kts`، اختبر الشيفرة، ثم أنشئ Commit جديدًا مع `[signed-release]`. لا تغيّر المخزن الخاص بالتوقيع، اسم الحزمة `com.bimspecialist.measure3d`، أو البصمة الموثوقة. 

## حماية القياسات

يمكن تصدير نسخة JSON من جميع المشاريع والقياسات من شاشة المشاريع داخل النسخة الجديدة. الاستعادة تتطلب موافقة واضحة لأنها تستبدل البيانات المحلية. الإصدار 0.3 القديم لا يملك هذه الخاصية؛ إذا كان مثبتًا لديك، صدّر القياسات المهمة منه بصيغ DXF/CSV قبل أي إزالة.

## الحالة المهنية

التوقيع والتحديثات يحلان مشكلة الاستمرارية، وليس دقة الكاميرا. يلزم فحص التطبيق على جهاز ARCore حقيقي قبل اعتماده للمسح الهندسي أو أعمال الإنشاء.