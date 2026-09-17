# 検証結果

実施日：2026-09-17。Room v2 の現行発表会基盤、最小 Compose 起動ルート、v1 移行テストを対象に確認した。

| コマンド | 結果 |
| --- | --- |
| `./gradlew test` | 成功（確定プログラム値スナップショットと学年進行の単体テスト） |
| `./gradlew :app:compileDebugAndroidTestKotlin` | 成功（Room 行動テストと v1 → v2 移行テストをコンパイル） |
| `./gradlew lint` | 成功 |
| `./gradlew assembleDebug` | 成功 |
| `./gradlew connectedDebugAndroidTest` | 未実行：接続済み端末／エミュレータがなく `No connected devices` |

`assembleDebug` の初回実行時に、既存依存ライブラリ `libandroidx.graphics.path.so` をシンボル除去できないというパッケージング通知が出る。APK 生成は成功しており、今回の変更によるコンパイルエラーではない。

## Room テストの対象

- Repository と DB の両方で、現行発表会を一件に制限すること。
- 当年参加者だけを演奏へ追加でき、使用中の参加者は解除できないこと。
- 担当講師が登録済み講師への一貫した参照であること。
- 年度終了で発表会固有データだけが消え、出演者・作曲家は残ること。
- 複数出演者・複数曲・表示順・曲ごとの作曲家表記を保持すること。
- 複合保存の失敗時のロールバックと、全子要素を要求する並べ替えを確認すること。
- `AppDatabaseMigrationTest` で v1 の既存データ、現行スロット、当年参加者の移行、担当講師外部キーを確認すること。

計測テストは APK まで生成済みである。端末またはエミュレータを接続した環境で `./gradlew connectedDebugAndroidTest` を再実行して、上記の Room 実行結果を確定する。
