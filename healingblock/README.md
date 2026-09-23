# Healing Block — Minecraft Java版 26.2 / Fabric 入門MOD

右クリックすると**体力と満腹度を全回復**させる「**回復の祭壇 (Healing Altar)**」ブロックを
追加する、初めてのMOD開発向けのシンプルなMODです。

* 使うと一度に全回復(体力・満腹度・満足度)
* 連発できないように**30秒のクールダウン**あり(使えないうちに右クリックすると
  チャット欄下部に「まだ使えません(残り◯秒)」と表示されます)
* 光源になる(明るさレベル7)ので、目印としても使えます

---

## 0. 最初に読んでください

このMODのJavaコードとアセット(テクスチャ・モデル・言語ファイル)は、このリポジトリの
`arcanemagic` MOD(削除済みだがGit履歴に残っている)で実際に確認済みの、
Minecraft 26.2 Fabric(Mojangの公式マッピング)向けの書き方にならって作られています。
このリポジトリを作った環境はインターネットに接続できないため、**実際にビルドして
動作確認する作業はお手元の環境で行ってください**。手順は以下の通りです。

---

## 1. 手順1: 正式なプロジェクトの土台を生成する(最重要)

1. ブラウザで **https://fabricmc.net/develop/template/** を開く
2. 以下を設定する
   * Mod Name: `Healing Block`(何でもOK)
   * Mod ID の下にある **「Use custom id」** を押して **`healingblock`**(ハイフンなし)に変更する
   * Package Name: **`com.healingblock`**(← このMODのJavaコードがこのパッケージ名を
     前提にしているので、必ず同じにしてください)
   * Minecraft Version: **26.2**
   * Advanced Options の4つのチェックボックス(Kotlin Programming Language /
     Data Generation / Split client and common sources / Kotlin Build Script)は
     **すべてチェックなしのまま**にする
3. 「Download Template (.ZIP)」を押してダウンロードして展開する
4. 展開してできたフォルダの中の `gradle.properties` と `build.gradle` が、実際に
   26.2向けにFabricが用意している最新の設定です。**このMOD側のものと入れ替える
   必要はありません**。生成されたものをそのまま使ってください。

---

## 2. 手順2: このフォルダの中身を、生成したテンプレートに上書きする

手順1で展開したテンプレートのフォルダ(以下「テンプレート側」)に、この `healingblock/` フォルダ
(以下「MOD側」)の中身を **上書きコピー** します。

| コピー元(MOD側) | コピー先(テンプレート側) | 備考 |
| --- | --- | --- |
| `src/main/java/com/healingblock/` | `src/main/java/com/healingblock/`(既存の例を削除してから) | テンプレートが生成した`ExampleMod.java`等のサンプルは削除してOK |
| `src/main/resources/fabric.mod.json` | 同名を上書き | |
| `src/main/resources/assets/healingblock/` | 同名フォルダに上書き | |

`build.gradle` / `settings.gradle` / `gradle.properties` は、**テンプレート側のものを
そのまま使ってください**(このMOD側の同名ファイルは上書きコピーしないでください)。

### フォルダ構成の最終イメージ

```
(テンプレートのルート)/
├── build.gradle          ← テンプレート由来(そのまま)
├── settings.gradle       ← テンプレート由来(そのまま)
├── gradle.properties     ← テンプレート由来(そのまま)
├── gradlew / gradlew.bat / gradle/wrapper/...   ← テンプレート由来(そのまま)
└── src/main/
    ├── java/com/healingblock/  ← MOD側からコピー(テンプレの例(ExampleMod.java等)は削除してから)
    └── resources/
        ├── fabric.mod.json            ← MOD側からコピー(上書き)
        └── assets/healingblock/       ← MOD側からコピー
```

---

## 3. 手順3: ビルド・起動してみる

1. IntelliJ IDEA(推奨・無料のCommunity版でOK)で、テンプレートのルートフォルダを開く
   (`build.gradle`を検知して自動でGradle同期が始まります)
2. `build.gradle` が `sourceCompatibility = JavaVersion.VERSION_25` のように **25** を
   要求している場合、JDK 25 が必要です。IntelliJの `File > Project Structure > SDKs` の
   「+」→「Download JDK...」から、バージョン25・ベンダー Eclipse Temurin を選んで
   ダウンロードしてください
3. 初回はMinecraftの逆コンパイル等でそこそこ時間がかかります(数分〜二十数分、回線速度次第)
4. 同期が終わったら、画面右側の Gradle パネルから
   `healingblock > Tasks > fabric > runClient` をダブルクリックすると、
   MODが読み込まれた状態のMinecraftが起動します
   (コマンドラインからなら `./gradlew runClient`)

### ゲーム内での確認方法

1. 新しいワールド(クリエイティブモード推奨)を作る
2. クリエイティブインベントリを開き、検索欄で「回復の祭壇」または「Healing」と入力
   (専用のクリエイティブタブにも入っています)
3. ブロックを設置して、体力を減らした状態(自分を殴る/`/damage`コマンド等)で右クリック
4. 体力・満腹度が全回復してハートのパーティクルが出れば成功です
5. 直後にもう一度右クリックすると「まだ使えません(残り◯秒)」と表示されるはずです

---

## 4. 動作の仕組み(コードの読み方)

* `HealingBlockMod.java` — MODのエントリーポイント。起動時に `ModBlocks.init()` と
  `ModItemGroup.init()` を呼んでいるだけです。
* `block/ModBlocks.java` — 「healing_altar」という名前でブロックとその設置用アイテムを
  ゲームに登録しています。硬さ・光源の明るさなどのブロックの性質もここで設定します。
* `block/HealingAltarBlock.java` — 本体のロジック。右クリック(`useWithoutItem`)が
  呼ばれたら、
  1. サーバー側だけで処理する(`!level.isClientSide`)
  2. クールダウン中かどうかを確認し、中ならメッセージだけ出して終了
  3. クールダウン中でなければ体力・満腹度を全回復し、次に使える時刻を30秒後に記録
  4. 効果音とハートのパーティクルを出す
* `item/group/ModItemGroup.java` — クリエイティブインベントリに専用タブ
  (「ヒーリングブロック」)を作り、このブロックを並べています。
* `assets/healingblock/` — 見た目まわり。`blockstates/` → `models/` → `textures/` の順に
  参照され、`lang/` にブロック名やメッセージの日本語・英語訳が入っています。

---

## 5. 調整できる数値

| 内容 | ファイル | 該当箇所 |
| --- | --- | --- |
| クールダウンの長さ | `block/HealingAltarBlock.java` | `COOLDOWN_TICKS = 600`(20 tick = 1秒) |
| 回復量(体力以外) | `block/HealingAltarBlock.java` | `setFoodLevel(20)` / `setSaturation(20.0F)` |
| ブロックの硬さ | `block/ModBlocks.java` | `.strength(3.0F, 6.0F)` |
| 明るさ | `block/ModBlocks.java` | `.lightLevel(state -> 7)` |
| 見た目 | `textures/block/healing_altar.png` | `scripts/gen_textures.py` で再生成、または好きな画像に差し替え |

---

## 6. 既知の制約 / 次のステップ案

* クールダウンはメモリ上(Javaの変数)に保存しているだけなので、**サーバーの再起動や
  `/reload` でリセット**されます。ワールドを保存してもクールダウン状態は保存されません。
  → 次の一歩として「ブロックエンティティ」を使い、NBTにクールダウンの残り時間を
    保存する仕組みを追加すると、再起動をまたいでも状態が保持されるようになります。
* クールダウンはプレイヤーごとではなく**ブロックごと**です(1つの祭壇を複数人で
  使い回すと、誰か1人が使った直後は他の人も30秒待つ必要があります)。
* クールダウンの管理は**座標(BlockPos)だけ**で行っています。そのため、別の
  ディメンション(ネザー等)の全く同じ座標にもう1つ祭壇を置いた場合、
  クールダウンを共有してしまいます(通常の使い方ではまず気にならない制約です)。
* テクスチャは `scripts/gen_textures.py` で自動生成した簡素なドット絵です。
  お好きな16x16のドット絵ツール(Aseprite等)で `textures/block/healing_altar.png` を
  上書きすれば、見た目をいくらでも変更できます。
* 次に足すと面白そうな機能の例:
  * クールダウン中はブロックの明るさを落とす/テクスチャを差し替える(blockstateの
    プロパティを追加)
  * 回復と同時に一定時間「再生」や「耐性」の効果を付与する
  * 特定のアイテム(例: 金リンゴ)を消費しないと使えないようにする

---

## ファイル構成

```
healingblock/
├── build.gradle / settings.gradle / gradle.properties   … テンプレート参考用(手順1参照)
├── scripts/gen_textures.py                               … テクスチャ生成スクリプト
└── src/main/
    ├── java/com/healingblock/
    │   ├── HealingBlockMod.java        … エントリーポイント
    │   ├── block/
    │   │   ├── ModBlocks.java          … ブロック登録
    │   │   └── HealingAltarBlock.java  … 回復ロジック本体
    │   └── item/group/ModItemGroup.java … クリエイティブタブ
    └── resources/
        ├── fabric.mod.json
        └── assets/healingblock/
            ├── blockstates/healing_altar.json
            ├── models/block/healing_altar.json
            ├── models/item/healing_altar.json
            ├── textures/block/healing_altar.png
            ├── icon.png
            └── lang/ja_jp.json, en_us.json
```
