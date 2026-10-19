#!/usr/bin/env bash
# 在本機打正式包，並發布到 GitHub Release（橘子版）。
#
# 簽名金鑰只留在你的電腦上，不會上傳到 GitHub；這也是不用 GitHub Actions 發版的原因。
#
# 用法（Git Bash / macOS / Linux）：
#   export HGTV_KEYSTORE=/d/code/hgtv-mod/hgtv-patch.jks   # 你的金鑰檔
#   export HGTV_KEY_ALIAS=hgtv
#   export HGTV_STORE_PASSWORD='…'                          # 自己輸入，不要寫進檔案
#   export HGTV_KEY_PASSWORD="$HGTV_STORE_PASSWORD"
#   tools/release.sh                 # 發正式版
#   tools/release.sh --prerelease    # 發預覽版（App 裡選「正式版与预览版」頻道的人才會收到）
#   tools/release.sh --dry-run       # 只打包並產生附件，不發布
#
# 也可以改用 android/keystore.properties（storeFile / storePassword / keyAlias / keyPassword，已列入 .gitignore）。
#
# 發布前會檢查：
#   - 工作區沒有未提交的修改，而且目前的提交已經推到 GitHub（GPL：發出去的 APK 要對得上公開的原始碼）；
#   - docs/CHANGELOG.md 有這一版的「## <版本號>：…」一節（Release 說明與 App 內更新說明都從這裡取）；
#   - 這個版本還沒發過（tag v<版本號> 不存在）。
#
# 產物放在 outputs/：HGTV-orange-<版本號>.apk、SHA256SUMS、update.json（App 內更新讀這個）、RELEASE_NOTES.md。
set -euo pipefail
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

prerelease=false
dry_run=false
for arg in "$@"; do
  case "$arg" in
    --prerelease) prerelease=true ;;
    --dry-run) dry_run=true ;;
    *) echo "用法：tools/release.sh [--prerelease] [--dry-run]" >&2; exit 2 ;;
  esac
done

repo="${HGTV_RELEASE_REPO:-orange0730/HGTV-Orange}"
version="$(tools/version.sh version)"
code="$(tools/version.sh code)"
min_sdk="$(tools/version.sh min-sdk)"
apk="$(tools/version.sh apk)"
tag="v$version"

# Windows 的 python3 / python 可能是只會跳出商店提示的假程式：挑第一個真的能跑 Python 3 的。
PY=
for candidate in python3 python py; do
  if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys; sys.exit(sys.version_info[0] != 3)' >/dev/null 2>&1; then
    PY=$candidate; break
  fi
done
[[ -n "$PY" ]] || { echo "需要 Python 3" >&2; exit 1; }

[[ -n "${HGTV_KEYSTORE:-}" || -f android/keystore.properties ]] \
  || { echo "沒有簽名設定：請設 HGTV_KEYSTORE 等環境變數，或建立 android/keystore.properties" >&2; exit 1; }

# 這一版的說明要先寫好（找不到時 release-notes.sh 會報錯）。
tools/release-notes.sh body >/dev/null

if ! $dry_run; then
  command -v gh >/dev/null || { echo "需要 GitHub CLI（gh）" >&2; exit 1; }
  gh auth status >/dev/null 2>&1 || { echo "gh 還沒登入：先執行 gh auth login" >&2; exit 1; }
  [[ -z "$(git status --porcelain)" ]] || { echo "工作區有未提交的修改，請先提交" >&2; exit 1; }
  commit="$(git rev-parse HEAD)"
  git fetch -q origin
  [[ -n "$(git branch -r --contains "$commit" 2>/dev/null)" ]] \
    || { echo "目前的提交 ${commit:0:7} 還沒推到 GitHub，請先 git push" >&2; exit 1; }
  if gh release view "$tag" --repo "$repo" >/dev/null 2>&1; then
    echo "$tag 已經發布過了：請先用 tools/bump-version.sh 升版本號" >&2; exit 1
  fi
fi

echo "== 測試、lint、打正式包：$apk（versionCode $code）"
./android/gradlew -p android :core:test :app:lintRelease :app:assembleRelease --console=plain

mkdir -p outputs
cp android/app/build/outputs/apk/release/app-release.apk "outputs/$apk"
(cd outputs && sha256sum "$apk" > SHA256SUMS)
size="$(wc -c < "outputs/$apk" | tr -d ' ')"
sha="$(cut -d' ' -f1 outputs/SHA256SUMS)"
notes="$(tools/release-notes.sh notes)"
tools/release-notes.sh body > outputs/RELEASE_NOTES.md

# update.json：App「检查更新 / 选择版本安装」讀的就是這個檔，欄位與 AppUpdate.parse 一一對應。
"$PY" - "$version" "$code" "$min_sdk" "$tag" "$apk" "$size" "$sha" "$notes" > outputs/update.json <<'PY'
import json, re, sys
version, code, min_sdk, tag, apk, size, sha, notes = sys.argv[1:9]
meta = {
    "schema": 1,
    "packageName": "tw.orange.hgtv",
    "versionName": version,
    "versionCode": int(code),
    "minSdk": int(min_sdk),
    "tag": tag,
    "notes": notes.strip(),
    "apk": {"name": apk, "size": int(size), "sha256": sha},
}
# 與 App 端相同的規則先檢查一次，發出去的檔案一定讀得懂。
assert re.fullmatch(r"[0-9][0-9A-Za-z.+-]{0,63}", meta["versionName"]), "版本名不符合規則"
assert re.fullmatch(r"v[0-9][0-9A-Za-z.+-]{0,80}", meta["tag"]), "tag 不符合規則"
assert re.fullmatch(r"HGTV-orange-[0-9][0-9A-Za-z.+-]{0,80}\.apk", meta["apk"]["name"]), "安裝包名不符合規則"
assert re.fullmatch(r"[0-9a-f]{64}", meta["apk"]["sha256"]), "校驗值不符合規則"
assert 26 <= meta["minSdk"] <= 100, "minSdk 不符合規則"
print(json.dumps(meta, ensure_ascii=False, indent=2))
PY

echo
echo "== 產物"
ls -l outputs/"$apk" outputs/SHA256SUMS outputs/update.json outputs/RELEASE_NOTES.md
cat outputs/SHA256SUMS

if $dry_run; then
  echo
  echo "--dry-run：只打包，沒有發布。"
  exit 0
fi

flags=()
$prerelease && flags+=(--prerelease)
echo
echo "== 發布 $tag 到 $repo（提交 ${commit:0:7}）"
gh release create "$tag" "outputs/$apk" outputs/SHA256SUMS outputs/update.json \
  --repo "$repo" --target "$commit" --title "$tag" --notes-file outputs/RELEASE_NOTES.md ${flags[@]+"${flags[@]}"}
echo "完成：https://github.com/$repo/releases/tag/$tag"
