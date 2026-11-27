# JAMPlayer Car 1.0.6 安裝相容性修正

日期：2026-11-27。手機版維持 1.0.14，未更動。

## 原因與修正

Android 10 模擬器安裝舊車機版 1.0.5 時，重現 INSTALL_FAILED_OLDER_SDK：Requires newer sdk version #31 (current version is #29)。舊 APK 最低要求 Android 12；使用者車機為 Android 10。

新車機版 1.0.6（10006）最低需求改為 Android 8.0（API 26），與手機版一致。保留 targetSdk 35、既有簽章與安裝識別，不變更車機 UI 或播放行為。測試輔助程式改用舊系統可用的串流讀取方式。

## 驗證

- 正式 release 建置及 lint 通過（0 errors，42 warnings）；78 項既有資料測試通過。
- 正式 APK 簽章驗證通過，封裝 UI 檔案與來源一致。
- Android 10（API 29）：正式 APK 成功安裝／啟動。
- Android 10：CarInstrumentation 通過本機 WAV、HTTP 音訊、記憶秒數續播、重複開啟、熄屏不中斷、缺檔等待、手動選擇與歌單設定導航。
- Android 10：SleepPriorityInstrumentation 通過四小時原生到期時間、手動優先、前背景不重設、熄屏、取消與到期行為；並非連續實播四小時。
- Android 10：QueueFocusInstrumentation 通過指定國家、全部、最愛、歌單、單曲循環與返回焦點定位。
- Android 14（API 34）：舊正式版 1.0.5 安裝後，直接覆蓋升級 1.0.6 及啟動成功。
- 手機 APK 的 SHA256 與既有 1.0.14 一致。

## 測試環境與範圍

Android 10 採 Google 官方模擬系統、WebView 113 與 192MB Java heap 設定。原始舊映像 WebView 74 不支援既有 UI 所用 JavaScript；測試前更新系統元件。未於 Android 8／9 或使用者實體車機驗證；實體 ACC 與廠商電源政策仍需裝置確認。
