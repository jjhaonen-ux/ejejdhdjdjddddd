# الإسناد والتراخيص — مختبر البرومبت لأندرويد

هذا المشروع (المرحلة الثانية) أعاد استخدام **قواعد وأنماطًا** من مصادر مرخصة MIT، وأعاد بناء التنفيذ من الصفر في Kotlin. لم تُنسَ شفرة من أي مشروع بلا ترخيص.

## ماذا استُخدم من أين

| المصدر | ما استُخدم | كيف |
|---|---|---|
| **Prompt Rerank** (MIT — Copyright (c) 2026 PromptPolish contributors) | بنية نموذج النص، القواعد الـ 12، إعداد التنقيط، كشف نوع المهمة والتعقيد، فكرة القواعد-ك-بيانات | نقل بقياس تكافؤ (11 ناقلًا) + إعادة كتابة Kotlin + توسعة عربية |
| **prompt-optimizer** (MIT — Copyright (c) 2026 prompt-optimizer contributors) | قواعد السلوك: النقل الحرفي، العناصر النائبة بدل الاختراع، سؤال الموضوع يمنع أسئلة أقل أثرًا، الطلب البسيط يبقى كما هو | إعادة صياغة كقواعد محرك، بلا نسخ نصوص SKILL.md |
| **PromptPerfect** (MIT — Copyright (c) 2026 Beagle-AI-automation) | فكرة فصل الناتج عن الشرح والتغييرات، حدود الاستخدام (للمرحلة الثالثة) | نمط تصميمي فقط |
| **Promptimizer** (بلا ترخيص ظاهر) | **لا شيء** — لم تُنسَ شفرة ولا قوالب | مرجع مفاهيمي مؤجل حتى توثيق الترخيص |

## نصوص الإشعارات الواجب ضمها عند التوزيع

```
Prompt Rerank — MIT License
Copyright (c) 2026 PromptPolish contributors

prompt-optimizer — MIT License
Copyright (c) 2026 prompt-optimizer contributors

PromptPerfect — MIT License
Copyright (c) 2026 Beagle-AI-automation
```

مع نص MIT القياسي الكامل (بند Permission is hereby granted… وبند الضم: «The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.»).

## التزام ما قبل التوزيع

1. توليد ملف `NOTICE` مجمّع يضم النصوص أعلاه + تراخيص تبعيات AndroidX/Compose/Room (تولد آليًا من Gradle عند البناء — أضف مهمة سكربت لجمعها).
2. إظهار الإسناد في شاشة «حول التطبيق» قبل النشر العام.
3. عدم استخدام أسماء أو شعارات المشاريع المصدرية في التسويق.
