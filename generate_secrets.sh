#!/bin/bash
set -euo pipefail

OUTPUT_FILE="github_secrets.txt"
KEYSTORE_FILE="release.jks"
ALIAS="datathrottle"

echo "=== DataThrottle 本番キーストア & GitHub Secrets 生成スクリプト ==="

# 1. 暗号論的疑似乱数(CSPRNG)で強力な24桁のランダムパスワードを生成
PASSWORD=$(python3 -c 'import secrets, string; print("".join(secrets.choice(string.ascii_letters + string.digits + "!@#$%^&*") for _ in range(24)))')

# 既存のキーストアがあれば削除
if [ -f "$KEYSTORE_FILE" ]; then
    rm -f "$KEYSTORE_FILE"
fi

# 2. 4096-bit RSA 本番キーストアを生成
keytool -genkeypair -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -dname "CN=DataThrottle Release, OU=Mobile, O=DataThrottle, L=Tokyo, ST=Tokyo, C=JP" \
    -noprompt

# 3. Base64 エンコード
BASE64_KEY=$(base64 -w 0 "$KEYSTORE_FILE")

# 4. github_secrets.txt に分かりやすく出力
cat <<EOF > "$OUTPUT_FILE"
======================================================================
DataThrottle GitHub Actions Secrets 設定一覧
GitHub の Settings > Secrets and variables > Actions に以下を登録してください。
※ 登録完了後、このファイル (github_secrets.txt) は削除してください。
======================================================================

[Secret 1]
Name: KEY_ALIAS
Value:
$ALIAS

----------------------------------------------------------------------
[Secret 2]
Name: STORE_PASSWORD
Value:
$PASSWORD

----------------------------------------------------------------------
[Secret 3]
Name: KEY_PASSWORD
Value:
$PASSWORD

----------------------------------------------------------------------
[Secret 4]
Name: KEYSTORE_BASE64
Value:
$BASE64_KEY

======================================================================
EOF

chmod 600 "$OUTPUT_FILE" "$KEYSTORE_FILE"

echo ""
echo "✅ 生成が完了しました！"
echo "📄 出力先: $OUTPUT_FILE"
echo ""
echo "以下のコマンドで内容を確認・コピーできます:"
echo "  cat $OUTPUT_FILE"
echo ""
echo "GitHub Secrets に登録完了後の削除コマンド:"
echo "  rm -f $OUTPUT_FILE"
echo "======================================================================"
