# 📚 دليل استخدام Git و GitHub - شامل ومفصل

**للمشروع:** joud-core  
**اللغة:** العربية 🇸🇦  
**آخر تحديث:** 2026-06-11

---

## 📖 جدول المحتويات

1. [المقدمة](#مقدمة)
2. [التثبيت والإعداد](#التثبيت-والإعداد)
3. [المفاهيم الأساسية](#المفاهيم-الأساسية)
4. [خطوات البداية](#خطوات-البداية)
5. [سير العمل اليومي](#سير-العمل-اليومي)
6. [إنشاء فروع جديدة](#إنشاء-فروع-جديدة)
7. [حفظ ورفع التغييرات](#حفظ-ورفع-التغييرات)
8. [النسخ الاحتياطية والعودة للإصدارات القديمة](#النسخ-الاحتياطية-والعودة-للإصدارات-القديمة)
9. [الأوامر المهمة](#الأوامر-المهمة)
10. [حل المشاكل الشائعة](#حل-المشاكل-الشائعة)
11. [Best Practices](#best-practices)

---

## 🎯 مقدمة

### ما هو Git؟
Git هو نظام تحكم بالإصدارات يسمح لك بـ:
- ✅ حفظ نسخ احتياطية من مشروعك
- ✅ تتبع جميع التغييرات
- ✅ العودة إلى نسخة قديمة في أي وقت
- ✅ العمل مع فريق دون تضارب

### ما هو GitHub؟
GitHub هو منصة سحابية تستضيف مشاريع Git وتسمح بـ:
- ☁️ حفظ المشروع على الإنترنت
- 👥 التعاون مع فريق
- 📊 إدارة الإصدارات والفروع

---

## 🔧 التثبيت والإعداد

### الخطوة 1: التحقق من تثبيت Git

```bash
git --version
```

**النتيجة المتوقعة:**
```
git version 2.54.0.windows.1
```

إذا لم يعمل، [حمّل Git من هنا](https://git-scm.com/download/win)

---

### الخطوة 2: إعداد معلوماتك الشخصية

قم بتشغيل هذه الأوامر **مرة واحدة فقط**:

```bash
git config --global user.name "اسمك الكامل"
git config --global user.email "بريدك@example.com"
```

**مثال:**
```bash
git config --global user.name "Rizgalah"
git config --global user.email "a.rizgalah@gmail.com"
```

### التحقق من الإعدادات:
```bash
git config --global user.name
git config --global user.email
```

---

### الخطوة 3: إعداد مدير سطور النهايات (Windows)

لتجنب التحذيرات عند الرفع:

```bash
git config --global core.autocrlf true
```

---

## 📚 المفاهيم الأساسية

### 1️⃣ **مستودع (Repository)**
```
📁 مشروع كامل يحتوي على جميع الملفات والسجلات
   └── 📂 .git (ملف مخفي يحفظ السجل)
```

### 2️⃣ **الفروع (Branches)**
```
نسخ من المشروع تسمح بالعمل المتوازي:

main (الفرع الرئيسي - الإصدار الثابت)
 │
 ├── feature/accounting (ميزة جديدة)
 ├── bugfix/login (إصلاح خطأ)
 └── develop (تطوير)
```

### 3️⃣ **Staging Area (منطقة التجهيز)**
```
Staging = تحضير الملفات قبل الحفظ

Working Directory → git add → Staging Area → git commit → Repository
(الملفات)          (التجهيز)   (المنطقة)    (الحفظ)       (السجل)
```

### 4️⃣ **Commit (حفظة)**
```
لقطة من المشروع في لحظة معينة مع وصف التغييرات

الحفظة 1: "إضافة نموذج المستخدم"
الحفظة 2: "إصلاح خطأ في المصادقة"
الحفظة 3: "إضافة قاعدة بيانات الموظفين"
```

### 5️⃣ **Push و Pull**
```
┌─ جهازك ─┐        ┌─ GitHub ─┐
│ محلي    │◄──Pull──│          │
│         │──Push──►│  سحابي   │
└─────────┘        └──────────┘
```

### 6️⃣ **التاريخ (History)**
```
🔴 النسخة الأصلية (الحفظة 1)
  ↓
🟡 إضافة ميزة (الحفظة 2)
  ↓
🟢 إصلاح خطأ (الحفظة 3)
  ↓
🔵 تحديث جديد (الحفظة 4)
  ↓ ← أنت هنا

يمكنك العودة إلى أي نسخة في أي وقت!
```

---

## 🚀 خطوات البداية

### المرة الأولى: استنساخ المشروع

#### 1. افتح Command Prompt في مجلد تريد فيه المشروع

```bash
cd C:\Projects
```

#### 2. استنسخ المشروع من GitHub

```bash
git clone https://github.com/Rizgalah/joud-core.git
cd joud-core
```

**ماذا يحدث:**
```
📥 يتم تحميل جميع الملفات من GitHub
📥 يتم إنشاء مجلد .git لتخزين السجل
✅ أنت الآن جاهز للعمل!
```

#### 3. تحقق من الحالة

```bash
git status
```

**النتيجة المتوقعة:**
```
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```

---

## 📅 سير العمل اليومي

### الصباح: جلب آخر التحديثات

قبل أن تبدأ العمل، حمّل أحدث نسخة من GitHub:

```bash
git pull origin main
```

**ماذا يفعل:**
```
✅ يحمل جميع التحديثات من GitHub
✅ يدمج التحديثات مع ملفاتك المحلية
```

---

### أثناء اليوم: العمل على الملفات

#### 1. عدّل الملفات بحرية

```
📝 افتح أي محرر نصوص (Visual Studio Code، IntelliJ، إلخ)
📝 عدّل ملفاتك كالعادة
📝 احفظ الملفات (Ctrl+S)
```

#### 2. تحقق من التغييرات

```bash
git status
```

**النتيجة:**
```
Changes not staged for commit:
  modified:   pom.xml
  modified:   src/main/java/com/joud/core/security/model/User.java

Untracked files:
  (use "git add <file>..." to include in what will be committed)
        src/main/java/com/joud/core/audit/AuditListener.java
```

---

### المساء: حفظ ورفع التغييرات

#### **الخطوة 1️⃣: جهز الملفات (Staging)**

```bash
# خيار أ: أضف ملف واحد
git add pom.xml

# خيار ب: أضف ملفات معينة
git add pom.xml src/main/java/com/joud/core/audit/AuditListener.java

# خيار ج: أضف جميع التغييرات (الأسهل)
git add .
```

#### **الخطوة 2️⃣: احفظ التغييرات (Commit)**

```bash
git commit -m "وصف التعديلات"
```

**أمثلة على الرسائل الجيدة:**
```bash
git commit -m "إضافة نموذج الموظف"
git commit -m "إصلاح خطأ في المصادقة"
git commit -m "تحديث المكتبات في pom.xml"
git commit -m "إضافة وحدة المحاسبة"
```

#### **الخطوة 3️⃣: ادفع إلى GitHub (Push)**

```bash
git push origin main
```

**ماذا يحدث:**
```
✅ يتم رفع جميع الحفظات (commits) إلى GitHub
✅ يصبح مشروعك محدثاً على السحابة
```

---

## 🌿 إنشاء فروع جديدة

### لماذا نستخدم الفروع؟

```
✅ العمل على ميزات جديدة دون التأثير على main
✅ إصلاح الأخطاء بشكل منعزل
✅ تجنب تضارب التغييرات
✅ نسخة آمنة يمكن العودة إليها
```

---

### الخطوة 1: عرض الفروع الموجودة

```bash
git branch
```

**النتيجة:**
```
* main           ← أنت هنا
  joud-hr
  feature/accounting
```

---

### الخطوة 2: إنشاء فرع جديد

```bash
git checkout -b feature/new-feature
```

**أو بالطريقة الحديثة:**
```bash
git switch -c feature/new-feature
```

**ماذا يحدث:**
```
✅ ينشئ فرع جديد باسم feature/new-feature
✅ ينقلك إلى الفرع الجديد تلقائياً
✅ جميع التغييرات تحفظ على هذا الفرع بشكل منعزل
```

---

### الخطوة 3: اعمل على الفرع الجديد

```bash
# عدّل الملفات
# اعمل على الميزة الجديدة

# جهز الملفات
git add .

# احفظ التغييرات
git commit -m "إضافة ميزة جديدة"

# ادفع الفرع إلى GitHub
git push origin feature/new-feature
```

---

### الخطوة 4: دمج الفرع مع main (على GitHub)

#### **الطريقة الأولى: عبر GitHub Web**

1. اذهب إلى: https://github.com/Rizgalah/joud-core
2. انقر على "Pull Requests"
3. انقر على "New Pull Request"
4. اختر:
   - **base:** main
   - **compare:** feature/new-feature
5. انقر "Create Pull Request"
6. انقر "Merge pull request"

#### **الطريقة الثانية: من Command Line**

```bash
# انتقل إلى main
git checkout main

# جلب آخر التحديثات
git pull origin main

# دمج الفرع
git merge feature/new-feature

# ادفع التحديثات
git push origin main
```

---

### الخطوة 5: حذف الفرع (اختياري)

```bash
# حذف محلي
git branch -d feature/new-feature

# حذف من GitHub
git push origin --delete feature/new-feature
```

---

## 💾 حفظ ورفع التغييرات - ملخص

### المسار الكامل في صورة بصرية:

```
┌─────────────────────────────────────────────────────┐
│                   سير العمل الكامل                   │
└─────────────────────────────────────────────────────┘

1️⃣ جهازك
   ↓
   📝 عدّل الملفات
   ↓
   git status (تحقق من التغييرات)
   ↓

2️⃣ منطقة التجهيز (Staging)
   ↓
   git add .
   ↓

3️⃣ السجل المحلي
   ↓
   git commit -m "رسالة"
   ↓

4️⃣ GitHub (السحابة)
   ↓
   git push origin main
   ↓

✅ انتهى! النسخة الاحتياطية محفوظة الآن
```

---

### مثال عملي كامل:

```bash
# 1. تحقق من وضعك الحالي
git status

# 2. جلب أحدث التحديثات
git pull origin main

# 3. عدّل ملفاتك في المحرر

# 4. جهز الملفات
git add .

# 5. احفظ التغييرات (نسخة احتياطية)
git commit -m "إضافة ميزة المحاسبة"

# 6. ادفع إلى GitHub (نسخة احتياطية على السحابة)
git push origin main

# 7. تحقق من النجاح
git status
```

---

## 🔄 النسخ الاحتياطية والعودة للإصدارات القديمة

### 🚨 السيناريو: حدثت كارثة!

```
✅ الإصدار الأول (كان يعمل بشكل صحيح)
  ↓
✅ الإصدار الثاني (إضافة ميزة جديدة)
  ↓
❌ الإصدار الثالث (خطأ! التطبيق توقف عن العمل)
  ↓
❓ كيف نعود للإصدار الثاني الذي كان يعمل؟
```

---

### 1️⃣ عرض السجل (History)

#### **عرض جميع الحفظات:**

```bash
git log
```

**النتيجة:**
```
commit a1b2c3d4e5f6 (HEAD -> main, origin/main)
Author: محمد علي <mohammad@gmail.com>
Date:   Wed Jun 11 09:29:53 2026 +0300

    إضافة ميزة المحاسبة - حدثت مشكلة

commit x9y8z7w6v5u4
Author: محمد علي <mohammad@gmail.com>
Date:   Wed Jun 11 09:05:50 2026 +0300

    إضافة وحدة الموارد البشرية - كان يعمل بشكل صحيح

commit p1q2r3s4t5u6
Author: محمد علي <mohammad@gmail.com>
Date:   Wed Jun 11 08:30:00 2026 +0300

    إنشاء المشروع الأولي
```

#### **عرض السجل بشكل مختصر:**

```bash
git log --oneline
```

**النتيجة:**
```
a1b2c3d (HEAD -> main) إضافة ميزة المحاسبة - حدثت مشكلة
x9y8z7w إضافة وحدة الموارد البشرية - كان يعمل بشكل صحيح
p1q2r3s إنشاء المشروع الأولي
```

#### **عرض بمعلومات أكثر تفصيلاً:**

```bash
git log --oneline --graph --all
```

---

### 2️⃣ العودة إلى إصدار قديم (3 طرق)

#### **الطريقة 1️⃣: عرض الإصدار القديم فقط (بدون تغيير)**

```bash
# عرض المشروع كما كان في الإصدار القديم
git checkout x9y8z7w

# الآن يمكنك مراجعة الملفات
# عندما تنتهي، عد إلى main
git checkout main
```

---

#### **الطريقة 2️⃣: العودة والتراجع عن الحفظة الأخيرة (Revert)**

```bash
# هذا ينشئ حفظة جديدة تلغي آخر حفظة
git revert HEAD

# ستفتح محرر النصوص لإدخال رسالة
# اكتب: "التراجع عن آخر تحديث - كانت هناك مشكلة"
# ثم احفظ (Ctrl+S) وأغلق المحرر

# ادفع التحديث
git push origin main
```

**المميزات:**
- ✅ يحتفظ بالسجل الكامل
- ✅ آمن جداً
- ✅ يرى الجميع التغييرات

---

#### **الطريقة 3️⃣: العودة القسرية (Reset) - حذف الحفظات**

```bash
# العودة إلى إصدار سابق
git reset --hard x9y8z7w

# سيحذف جميع الحفظات بعد هذا الإصدار نهائياً!
# احذر: هذا تغيير دائم

# لأجل العودة إلى GitHub بنفس الطريقة
git push origin main --force-with-lease
```

**⚠️ تحذير:**
- ❌ خطير جداً!
- ❌ يفقد البيانات
- ❌ استخدمه فقط إذا كنت متأكداً 100%

---

### 3️⃣ الطريقة الآمنة (الموصى بها)

#### **الخطوة 1: إنشاء فرع جديد من الإصدار الجيد**

```bash
# عد إلى main أولاً
git checkout main

# إنشاء فرع جديد من الإصدار الجيد
git checkout -b recovery/restore-good-version x9y8z7w

# الآن أنت على فرع آمن مع الإصدار الجيد
```

#### **الخطوة 2: عدّل المشكلة**

```bash
# عدّل الملفات الخاطئة
# احفظ الملفات

# جهز الملفات
git add .

# احفظ التغييرات
git commit -m "إصلاح المشكلة في الإصدار الجيد"

# ادفع الفرع
git push origin recovery/restore-good-version
```

#### **الخطوة 3: دمج الفرع مع main**

```bash
# انتقل إلى main
git checkout main

# دمج الفرع الإصلاحي
git merge recovery/restore-good-version

# ادفع إلى GitHub
git push origin main
```

---

### 4️⃣ الحالات العملية

#### **الحالة 1: أضفت ملف بالخطأ**

```bash
# إذا لم تحفظ الملف بعد
git restore filename.java

# إذا حفظته
git reset HEAD~1
git restore filename.java
```

---

#### **الحالة 2: عدّلت ملف بالخطأ**

```bash
# إرجاع الملف إلى الإصدار الأخير
git restore src/main/java/MyFile.java

# أو من إصدار محدد
git checkout x9y8z7w -- src/main/java/MyFile.java
```

---

#### **الحالة 3: حذفت ملف مهم بالخطأ**

```bash
# البحث عن الملف في السجل
git log --all --full-history -- src/main/java/ImportantFile.java

# استرجاع الملف من أحدث إصدار
git restore src/main/java/ImportantFile.java

# أو من إصدار محدد
git checkout x9y8z7w -- src/main/java/ImportantFile.java

# احفظ التغييرات
git add .
git commit -m "استرجاع الملف المهم المحذوف"
git push origin main
```

---

#### **الحالة 4: أريد رؤية ما تغير بين إصدارين**

```bash
# مقارنة الإصدار الحالي مع إصدار سابق
git diff x9y8z7w HEAD

# مقارنة ملف محدد
git diff x9y8z7w HEAD -- src/main/java/MyFile.java

# عرض ملف معين في إصدار قديم
git show x9y8z7w:src/main/java/MyFile.java
```

---

### 5️⃣ البحث عن متى حدثت المشكلة (Bisect)

إذا أردت معرفة أي حفظة تسببت في المشكلة:

```bash
# ابدأ البحث
git bisect start

# حدد الإصدار الخاطئ الحالي
git bisect bad HEAD

# حدد إصدار قديم كان يعمل بشكل صحيح
git bisect good x9y8z7w

# الآن Git سيساعدك بالبحث التدريجي
# قل له إذا كان الإصدار الحالي جيد أو خاطئ
git bisect good    # أو bad

# عند الانتهاء
git bisect reset
```

---

## 🎯 الأوامر المهمة

### الأوامر الأساسية:

| الأمر | الوصف | الاستخدام |
|------|-------|----------|
| `git clone <url>` | استنساخ مشروع | المرة الأولى فقط |
| `git status` | عرض الحالة الحالية | كل وقت |
| `git add .` | تجهيز جميع الملفات | قبل الحفظ |
| `git commit -m "رسالة"` | حفظ التغييرات | بعد التجهيز |
| `git push origin main` | رفع إلى GitHub | بعد الحفظ |
| `git pull origin main` | جلب من GitHub | قبل العمل |
| `git branch` | عرض الفروع | أي وقت |
| `git checkout -b name` | إنشاء فرع جديد | عند بدء ميزة |

---

### أوامر النسخ الاحتياطية والعودة:

| الأمر | الوصف | الخطورة |
|------|-------|--------|
| `git log --oneline` | عرض السجل | ✅ آمن |
| `git show <commit>` | عرض حفظة محددة | ✅ آمن |
| `git diff <commit1> <commit2>` | مقارنة بين إصدارين | ✅ آمن |
| `git checkout <commit>` | عرض إصدار قديم | ✅ آمن |
| `git restore <file>` | إرجاع ملف | ✅ آمن |
| `git revert <commit>` | التراجع بشكل آمن | ✅ آمن |
| `git reset --hard <commit>` | العودة القسرية | ⚠️ خطير جداً |

---

### أوامر متقدمة:

```bash
# العثور على آخر حفظة تعديل على ملف
git log -n 1 -- src/main/java/MyFile.java

# عرض من عدّل كل سطر في الملف
git blame src/main/java/MyFile.java

# العثور على حفظة تحتوي على كلمة معينة
git log --grep="المحاسبة"

# عرض الملفات المعدلة في حفظة محددة
git show --name-status x9y8z7w

# دمج يدويّ للفروع
git merge branch-name

# إعادة قاعدة (rebase)
git rebase main

# البحث عن الحفظة التي تسببت في المشكلة
git bisect start
```

---

## 🚨 حل المشاكل الشائعة

### المشكلة 1: "rejected... fetch first"

**السبب:** GitHub يحتوي على تحديثات أنت لم تحملها

**الحل:**
```bash
git pull origin main
git push origin main
```

---

### المشكلة 2: "fatal: not a git repository"

**السبب:** أنت لم تنسخ المشروع أو في مجلد خاطئ

**الحل:**
```bash
cd c:\joud-core
git status
```

---

### المشكلة 3: تعارض في الملفات (Conflict)

**السبب:** أنت وشخص آخر عدّلتما نفس الملف

**الحل:**
```bash
# 1. جلب التحديثات
git pull origin main

# 2. فتح الملف المتعارض وحل التعارض يدويّاً
# ستجد علامات مثل:
# <<<<<<< HEAD
# كودك
# =======
# الكود الآخر
# >>>>>>> branch-name

# 3. احذف العلامات واحتفظ بالكود الصحيح

# 4. احفظ التغييرات
git add .
git commit -m "حل التعارض"
git push origin main
```

---

### المشكلة 4: نسيت رسالة في الحفظة (Commit)

**الحل:**
```bash
# عدّل آخر حفظة
git commit --amend -m "الرسالة الصحيحة"

# أو أضف ملف نسيته
git add file.txt
git commit --amend --no-edit
```

---

### المشكلة 5: أضفت ملفاً بالخطأ

**الحل:**
```bash
# إزالة من منطقة التجهيز (قبل commit)
git restore --staged file.txt

# أو إزالة من السجل (بعد commit)
git reset HEAD~1
```

---

### المشكلة 6: حدثت كارثة! كيف أعود للإصدار السابق؟

**الحل الآمن:**
```bash
# 1. عرض السجل
git log --oneline

# 2. أنشئ فرع من الإصدار الجيد
git checkout -b recovery/fix x9y8z7w

# 3. ادفع الفرع
git push origin recovery/fix

# 4. عدّل المشكلة
# ... (اعمل على الملفات)

# 5. احفظ وادفع
git add .
git commit -m "إصلاح المشكلة"
git push origin recovery/fix

# 6. دمج مع main
git checkout main
git merge recovery/fix
git push origin main
```

---

## ⭐ Best Practices

### 1️⃣ رسائل الحفظ (Commit Messages)

**✅ جيد:**
```bash
git commit -m "إضافة نموذج الموظف"
git commit -m "إصلاح خطأ في تسجيل الدخول"
git commit -m "تحديث المكتبات"
```

**❌ سيء:**
```bash
git commit -m "تعديلات"
git commit -m "خطأ"
git commit -m "update"
```

---

### 2️⃣ الحفظ المتكرر

**✅ أفضل:**
- احفظ كل جزء صغير من العمل
- احفظ عند انتهاء كل ميزة
- رسالة واضحة لكل حفظة

**❌ تجنب:**
- حفظ كل ساعة دون تحديثات حقيقية
- حفظ عدة ميزات في حفظة واحدة

---

### 3️⃣ استخدام الفروع

**✅ الطريقة الصحيحة:**
```
main (الإصدار الثابت)
├── feature/accounting (ميزة المحاسبة)
├── feature/hr (ميزة الموارد البشرية)
└── bugfix/login-error (إصلاح خطأ)
```

**❌ تجنب:**
```
العمل مباشرة على main وتحديثه باستمرار
```

---

### 4️⃣ جلب التحديثات قبل العمل

**✅ كل يوم:**
```bash
git pull origin main
```

**❌ لا تنسَ:**
```
بدء العمل مباشرة دون جلب التحديثات
```

---

### 5️⃣ ملف .gitignore

أضف ملفات لا تريد رفعها:

**إنشاء الملف:**
```bash
echo target/ > .gitignore
echo *.class >> .gitignore
echo .idea/ >> .gitignore
echo .DS_Store >> .gitignore
```

**محتوى النموذج:**
```
# Compiled files
target/
*.class
*.jar
*.war

# IDE
.idea/
.vscode/
*.swp

# OS
.DS_Store
Thumbs.db

# Dependencies
node_modules/
```

---

## 📋 الخلاصة - الخطوات الأساسية يومياً

### كل صباح:
```bash
git pull origin main
```

### أثناء اليوم:
```bash
# عدّل الملفات بحرية
# نسخة احتياطية تلقائية بفضل Git!
```

### قبل الانصراف:
```bash
git add .
git commit -m "وصف العمل اليومي"
git push origin main
```

### في حالة الطوارئ:
```bash
# 1. عرض السجل
git log --oneline

# 2. العودة بأمان
git checkout -b recovery/fix <commit-id>

# 3. إصلاح المشكلة
# ... اعمل على الملفات ...

# 4. احفظ وادفع
git add .
git commit -m "إصلاح"
git push origin recovery/fix
git checkout main
git merge recovery/fix
git push origin main
```

---

## 🔗 روابط مهمة

- 🌐 موقع GitHub: https://github.com
- 📚 توثيق Git: https://git-scm.com/doc
- 🎓 دليل GitHub: https://docs.github.com
- 📖 دليل Git الكامل: https://git-scm.com/book/ar/v2

---

## 💬 أسئلة شائعة

### س: هل يمكن حذف commit؟
**ج:** نعم! استخدم `git revert` (آمن) أو `git reset` (خطير)

### س: كم مرة يجب أن أحفظ (commit)؟
**ج:** عند انتهاء كل ميزة أو إصلاح صغير (كل ساعة تقريباً)

### س: ماذا لو فقدت ملف؟
**ج:** استخدم `git log` و `git checkout` للعودة إليه

### س: هل يمكن العمل بدون إنترنت؟
**ج:** نعم! Git يعمل محلياً. استخدم `push` و `pull` عند توفر الإنترنت

### س: كيف أرجع آخر تغيير بطريقة آمنة؟
**ج:** استخدم `git revert` - ينشئ حفظة جديدة تلغي التغيير دون حذف السجل

### س: هل يمكن استرجاع ملف محذوف؟
**ج:** نعم! `git restore <filename>` أو `git checkout <commit> -- <filename>`

---

## 📝 ملاحظات مهمة

> ⚠️ **احذر:** لا تعدّل `.git` - هذا المجلد المخفي يحفظ كل السجل

> 💡 **نصيحة:** استخدم `git status` باستمرار لتعرف أين أنت

> 🔐 **أمان:** استخدم Personal Access Token بدلاً من كلمة المرور على GitHub

> 📊 **تتبع:** احتفظ برسائل commit واضحة لتسهيل التتبع لاحقاً

> 🔄 **نسخ احتياطية:** Git يحفظ كل نسخة! لا تقلق من فقدان البيانات

> 🛡️ **الفروع آمنة:** استخدم فروع منفصلة للعمل الجديد، main دائماً آمن

---

## 🎓 دليل سريع للحالات الطارئة

```
📍 مشكلة: تطبيقي توقف عن العمل بعد آخر تحديث

🔧 الحل السريع:
1. git log --oneline              (عرض السجل)
2. git checkout -b recovery/fix <commit-id>  (إنشاء فرع آمن)
3. git push origin recovery/fix    (حفظ نسخة احتياطية)
4. (عدّل المشكلة)
5. git add . && git commit -m "إصلاح"
6. git push origin recovery/fix
7. git checkout main
8. git merge recovery/fix
9. git push origin main
```

---

**آخر تحديث:** 2026-06-11 ✅  
**أنشأه:** Rizgalah  
**المشروع:** joud-core

---

## 📌 رسالة خاصة

**هذا الملف هو دليلك الشامل للعمل بأمان!** 🛡️

كل حفظة تقوم بها محفوظة على GitHub بأمان كامل. يمكنك العودة إلى أي إصدار قديم في أي وقت بدون خسارة أي بيانات.

**تذكر دائماً:**
- ✅ احفظ كل تغيير مهم
- ✅ استخدم رسائل واضحة
- ✅ ادفع إلى GitHub يومياً
- ✅ استخدم فروع للعمل الجديد

**أنت الآن محمي! 🎯**
