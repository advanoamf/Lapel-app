# הקמת השרת ב-AWS — פעם אחת, כ-10 דקות

השרת רץ בחשבון ה-AWS שלך, בתוך מה ש-AWS נותנים בחינם לתמיד (Lambda + DynamoDB).
אין שרת קבוע ואין IP. אחרי ההקמה, GitHub מעלה ומעדכן את השרת לבד.

## שלב 1 — להוריד את קובץ ההקמה
1. בגיטהאב, בענף `claude/lapel-pin-app-architecture-nicohd`, פתח את הקובץ `infra/bootstrap.yaml`.
2. לחץ על כפתור ההורדה (⬇ **Download raw file**) בצד ימין למעלה. הקובץ יישמר במחשב.

## שלב 2 — להריץ אותו ב-AWS
1. היכנס ל-https://console.aws.amazon.com.
2. **למעלה מימין** בחר אזור: **Europe (Frankfurt) eu-central-1**. חשוב — כל השרת יהיה שם.
3. בשורת החיפוש למעלה כתוב **CloudFormation** ופתח.
4. **Create stack** → **With new resources (standard)**.
5. **Upload a template file** → **Choose file** → בחר את `bootstrap.yaml` → **Next**.
6. **Stack name:** `lapel-bootstrap`
   **AlertEmail:** המייל שלך (לשם תגיע התראה אם החיוב החודשי עובר 50 סנט).
   השאר את שאר השדות כמו שהם → **Next** → **Next**.
7. בתחתית הדף סמן ✔ את התיבה **I acknowledge that AWS CloudFormation might create IAM resources with custom names** → **Submit**.
8. חכה כדקה עד שהסטטוס יהיה **CREATE_COMPLETE** (ירוק). אם צריך לרענן — כפתור 🔄.
9. לשונית **Outputs** → העתק את הערך של **DeployRoleArn**
   (נראה כמו `arn:aws:iam::123456789012:role/lapel-github-deploy`).

> אם יצא שגיאה על `token.actions.githubusercontent.com` שכבר קיים — מחק את ה-stack, וחזור על שלב 2 עם **CreateGitHubOidcProvider = false**.

## שלב 3 — להכניס שני ערכים בגיטהאב
1. ב-GitHub, בריפו **Lapel-app** → **Settings** → בתפריט משמאל **Secrets and variables** → **Actions**.
2. **New repository secret**:
   - Name: `AWS_DEPLOY_ROLE_ARN`  Value: הערך שהעתקת בשלב 2.9 → **Add secret**.
3. **New repository secret** שוב:
   - Name: `LAPEL_PASSWORD`  Value: סיסמה שתבחר לכניסה מהמחשב ומהאפליקציה — **לפחות 10 תווים, בלי רווחים ובלי פסיקים** (אותיות באנגלית, ספרות וסימנים כמו ! @ # מותרים) → **Add secret**.

זהו. תגיד לי כשסיימת ואפעיל את ההעלאה הראשונה של השרת.

## מה זה עולה
| שירות | מה כלול בחינם לתמיד | שימוש צפוי שלך |
|---|---|---|
| Lambda | מיליון בקשות + 400K GB-שניות בחודש | כמה אלפים בחודש |
| DynamoDB | 25GB, 25 יחידות קריאה/כתיבה | פחות מ-1MB |
| CloudWatch Logs | 5GB בחודש | מגהבייטים בודדים |
| S3 (קובץ הקוד של השרת) | — | ~20MB ≈ חצי סנט בחודש |
| Budgets (התראת התקציב) | 2 תקציבים בחינם | 1 |
