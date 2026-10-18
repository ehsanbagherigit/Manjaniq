دوستان این پروژه به لطف پترنیهای عزیز پا برجاست.ایشون رو حمایت کنید.
اینم نسخه اپن سورس اندروید:
https://github.com/rstagit/rstang

گروه پروژه داخل تلگرام:
https://t.me/rstasnispoof



# RSTA SNI Spoof 

ابزار دور زدن فیلترینگ با روش جعل SNI و فرگمنتیشن TLS  
نوشته شده به زبان Go — بدون نیاز به روت — قابل اجرا روی Termux/Android/Linux

---

## ویژگی‌ها

- جعل SNI برای فریب سیستم‌های DPI
- فرگمنتیشن TLS ClientHello با روش‌های مختلف
- ترفند TTL برای گیج کردن فایروال
- مانیتور real-time اتصال‌ها
- بدون نیاز به روت
- پشتیبانی از Termux / Android / Linux / macOS

---

## نصب و اجرا روی Termux

```bash
pkg install golang git
git clone https://github.com/rstagit/rstaspoof.git
cd rstaspoof
go run rstaspoof.go -tddd
```

---

## روش‌های استفاده

**حالت سریع (پیش‌فرض):**
```bash
go run rstaspoof.go -tddd
```

**حالت دستی=> در صورتی که پیش فرض کار نداد:**
آی پی تمیز کلود فلرو اسکن کنین جایگزین کنین.
```bash
go run rstaspoof.go -listen :40443 -connect IP:443 -sni www.hcaptcha.com -method combined
```

**کامپایل و اجرا:**
```bash
go build -o rstaspoof rstaspoof.go
./rstaspoof -tddd
```

---

## آرگومان‌ها

| آرگومان | توضیح | پیش‌فرض |
|--------|-------|---------|
| `-listen` | آدرس و پورت پروکسی محلی | `0.0.0.0:40443` |
| `-connect` | سرور مقصد | `104.18.38.202:443` |
| `-sni` | دامنه جعلی برای SNI | `cdnjs.cloudflare.com` |
| `-method` | روش bypass | `fragment` |
| `-fragment-strategy` | استراتژی فرگمنت | `sni_split` |
| `-fragment-delay` | تأخیر بین فرگمنت‌ها (ثانیه) | `0.1` |
| `-ttl-trick` | فعال‌سازی ترفند TTL | - |
| `-config` | بارگذاری تنظیمات از فایل JSON | - |
| `-quiet` | حذف خروجی غیرضروری | - |
| `-no-monitor` | غیرفعال کردن مانیتور | - |
| `-info` | نمایش قابلیت‌های سیستم | - |
| `-generate-config` | ساخت فایل تنظیمات پیش‌فرض | - |

---

## روش‌های Bypass

| روش | توضیح |
|-----|-------|
| `fragment` | تقسیم ClientHello در محل SNI. بدون نیاز به روت |
| `fake_sni` | ارسال SNI جعلی با ترفند TTL |
| `combined` | ترکیب هر دو روش — بهترین نتیجه |

---

## استراتژی‌های فرگمنت

| استراتژی | توضیح |
|---------|-------|
| `sni_split` | تقسیم دقیق در محل مقدار SNI *(پیشنهادی)* |
| `half` | تقسیم به دو نیم |
| `multi` | تقسیم به تکه‌های ۲۴ بایتی |
| `tls_record_frag` | فرگمنتیشن در سطح TLS record |

---

## فایل تنظیمات (JSON)

```bash
go run rstaspoof.go -generate-config config.json
go run rstaspoof.go -config config.json
```

---

## تنظیم برنامه

بعد از اجرا، پروکسی روی `127.0.0.1:40443` فعال می‌شه.  
در برنامه‌ای که می‌خوای از پروکسی استفاده کنه، آدرس پروکسی رو روی:

```
127.0.0.1:40443
```

تنظیم کن.

---

## گروه تلگرام

برای پشتیبانی، سوال و به‌روزرسانی‌ها عضو گروه تلگرام بشید:

**[@rstasnispoof](https://t.me/rstasnispoof)**

---
